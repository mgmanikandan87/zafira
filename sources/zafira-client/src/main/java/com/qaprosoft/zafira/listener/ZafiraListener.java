package com.qaprosoft.zafira.listener;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.configuration2.CombinedConfiguration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.MergeCombiner;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.SkipException;

import com.qaprosoft.zafira.client.ZafiraClient;
import com.qaprosoft.zafira.client.ZafiraClient.Response;
import com.qaprosoft.zafira.config.CIConfig;
import com.qaprosoft.zafira.config.CIConfig.BuildCasue;
import com.qaprosoft.zafira.config.IConfigurator;
import com.qaprosoft.zafira.models.db.Status;
import com.qaprosoft.zafira.models.db.TestRun.Initiator;
import com.qaprosoft.zafira.models.dto.JobType;
import com.qaprosoft.zafira.models.dto.TestCaseType;
import com.qaprosoft.zafira.models.dto.TestRunType;
import com.qaprosoft.zafira.models.dto.TestSuiteType;
import com.qaprosoft.zafira.models.dto.TestType;
import com.qaprosoft.zafira.models.dto.UserType;
import com.qaprosoft.zafira.models.dto.config.ConfigurationType;

/**
 * TestNG listener that provides integration with Zafira reporting web-service.
 * Accumulates test results and handles rerun failures logic.
 * 
 * @author akhursevich
 */
public class ZafiraListener implements ISuiteListener, ITestListener
{
	private static final Logger LOGGER = LoggerFactory.getLogger(ZafiraListener.class);
	
	private static final String ZAFIRA_PROPERTIES = "zafira.properties";
	
	private static final String ANONYMOUS = "anonymous";
	
	private static final String ZAFIRA_PROJECT_PARAM = "zafira_project";
	private static final String ZAFIRA_RUN_ID_PARAM = "zafira_run_id";
	
	private boolean ZAFIRA_ENABLED = false;
	private String 	ZAFIRA_URL = null;
	private String 	ZAFIRA_USERNAME = null;
	private String 	ZAFIRA_PASSWORD = null;
	private String 	ZAFIRA_PROJECT = null;
	private String 	ZAFIRA_REPORT_EMAILS = null;
	private boolean ZAFIRA_RERUN_FAILURES = false;
	private String 	ZAFIRA_CONFIGURATOR = null;
	
	private String JIRA_SUITE_ID = null;
	
	private IConfigurator configurator;
	private CIConfig ci;
	private ZafiraClient zc;
	
	private UserType user = null;
	private JobType parentJob = null;
	private JobType job = null;
	private TestSuiteType suite = null;
	private TestRunType run = null;
	private Map<String, TestType> registeredTests = new HashMap<>();
	private final ConcurrentHashMap<Long, TestType> testByThread = new ConcurrentHashMap<Long, TestType>();
	
	private Marshaller marshaller;
	
	@Override
	public void onStart(ISuite suiteСontext)
	{
		if(initializeZafira())
		{
			try
			{
				marshaller = JAXBContext.newInstance(ConfigurationType.class).createMarshaller();
				
				configurator = (IConfigurator) Class.forName(ZAFIRA_CONFIGURATOR).newInstance();
				
				// Override project if specified in XML
				String project = suiteСontext.getXmlSuite().getParameter(ZAFIRA_PROJECT_PARAM);
				zc.setProject(!StringUtils.isEmpty(project) ? project : ZAFIRA_PROJECT);
				
				// Register user who initiated test run
				this.user = zc.registerUser(ci.getCiUserId(), ci.getCiUserEmail(), ci.getCiUserFirstName(), ci.getCiUserLastName());
		
				// Register test suite along with suite owner
				UserType suiteOwner = zc.registerUser(configurator.getOwner(suiteСontext), null, null, null);
				this.suite = zc.registerTestSuite(suiteСontext.getName(), FilenameUtils.getName(suiteСontext.getXmlSuite().getFileName()), suiteOwner.getId());
				
				// Register job that triggers test run
				this.job = zc.registerJob(ci.getCiUrl(), suiteOwner.getId());
				
				// Register upstream job if required
				UserType anonymous = null;
				if (BuildCasue.UPSTREAMTRIGGER.equals(ci.getCiBuildCause())) 
				{
					anonymous = zc.registerUser(ANONYMOUS, null, null, null);
					parentJob = zc.registerJob(ci.getCiParentUrl(), anonymous.getId());
				}
				
				// Searching for existing test run with same CI run id in case of rerun
				if(!StringUtils.isEmpty(ci.getCiRunId())) 
				{
					Response<TestRunType> response = zc.getTestRunByCiRunId(ci.getCiRunId());
					this.run = response.getObject();
				}
				
				if (this.run != null) 
				{
					// Already discovered run with the same CI_RUN_ID, it is re-run functionality!
					// Reset build number for re-run to map to the latest rerun build 
					this.run.setBuildNumber(ci.getCiBuild());
					// Re-register test run to reset status onto in progress
					Response<TestRunType> response = zc.startTestRun(this.run);
					this.run = response.getObject();
					
					for(TestType test : Arrays.asList(zc.getTestRunResults(run.getId()).getObject()))
					{
						registeredTests.put(test.getName(), test);
					}
				} 
				else 
				{
					if(ZAFIRA_RERUN_FAILURES) 
					{
						LOGGER.error("Unable to find data in Zafira Reporting Service with CI_RUN_ID: '" + ci.getCiRunId() + "'.\n" + "Rerun failures featrure will be disabled!");
						ZAFIRA_RERUN_FAILURES = false;
					}
					// Register new test run
					boolean classMode = configurator.isClassMode();
					
					switch (ci.getCiBuildCause())
					{
						case UPSTREAMTRIGGER:
							this.run = zc.registerTestRunUPSTREAM_JOB(suite.getId(), convertToXML(configurator.getConfiguration()), job.getId(), parentJob.getId(), ci, Initiator.UPSTREAM_JOB, JIRA_SUITE_ID, classMode);
							break;
						case TIMERTRIGGER:
							this.run = zc.registerTestRunBySCHEDULER(suite.getId(), convertToXML(configurator.getConfiguration()), job.getId(), ci, Initiator.SCHEDULER, JIRA_SUITE_ID, classMode);
							break;
						case MANUALTRIGGER:
							this.run = zc.registerTestRunByHUMAN(suite.getId(), user.getId(), convertToXML(configurator.getConfiguration()), job.getId(), ci, Initiator.HUMAN, JIRA_SUITE_ID, classMode);
							break;
						default:
							throw new RuntimeException("Unable to register test run for zafira service: " + ZAFIRA_URL + " due to the misses build cause: '" + ci.getCiBuildCause() + "'");
					}
				}
				
				if (this.run == null) 
				{
					throw new RuntimeException("Unable to register test run for zafira service: " + ZAFIRA_URL);
				}
				else
				{
					System.setProperty(ZAFIRA_RUN_ID_PARAM, String.valueOf(this.run.getId()));
				}
			}
			catch (Exception e) 
			{
				ZAFIRA_ENABLED = false;
				LOGGER.error("Undefined error during test run registration!", e);
			}
		}
	}
	
	@Override
	public void onTestStart(ITestResult result)
	{
		if(!ZAFIRA_ENABLED) return;
		
		try 
		{
			TestType startedTest = null;
			
			String testName = configurator.getTestName(result);

			// If method owner is not specified then try to use suite owner. If both are not declared then ANONYMOUS will be used.
			String owner = !StringUtils.isEmpty(configurator.getOwner(result)) ? configurator.getOwner(result) : configurator.getOwner(result.getTestContext().getSuite());
			UserType methodOwner = zc.registerUser(owner, null, null, null);
			LOGGER.debug("methodOwner: " + methodOwner);
			
			String testClass = result.getMethod().getTestClass().getName();
			String testMethod = configurator.getTestMethodName(result);

			TestCaseType testCase = zc.registerTestCase(this.suite.getId(), methodOwner.getId(), testClass, testMethod);

			// Search already registered test!
			if(registeredTests.containsKey(testName))
			{
				startedTest = registeredTests.get(testName);
				
				// Skip already passed tests if rerun failures enabled
				if(ZAFIRA_RERUN_FAILURES && !startedTest.isNeedRerun())
				{
					throw new SkipException("ALREADY_PASSED: " + testName);
				}
				
				startedTest.setFinishTime(null);
				startedTest.setStartTime(new Date().getTime());
				startedTest = zc.registerTestRestart(startedTest);
			}
			
			if (startedTest == null) 
			{
				//new test run registration
				String testArgs = result.getParameters().toString();
				
				String group = result.getMethod().getTestClass().getName();
				group = group.substring(0, group.lastIndexOf("."));

				startedTest = zc.registerTestStart(testName, group, Status.IN_PROGRESS, testArgs, run.getId(), testCase.getId(), configurator.getDemoURL(result), configurator.getLogURL(result), configurator.getRunCount(result), convertToXML(configurator.getConfiguration()));
			}
			
			zc.registerWorkItems(startedTest.getId(), configurator.getTestWorkItems(result));
			// TODO: investigate why we need it
			testByThread.put(Thread.currentThread().getId(), startedTest);
			
			registeredTests.put(testName, startedTest);
		} 
		catch(SkipException e)
		{
			throw e;
		}
		catch (Exception e) 
		{
			LOGGER.error("Undefined error during test case/method start!", e);
		}
	}
	
	@Override
	public void onTestSuccess(ITestResult result)
	{
		if(!ZAFIRA_ENABLED) return;

		try 
		{
			Response<TestType> rs = zc.finishTest(populateTestResult(result, Status.PASSED, getFullStackTrace(result)));
			if(rs.getStatus() != 200 && rs.getObject() == null)
			{
				throw new RuntimeException("Unable to register test " + rs.getObject().getName() + " for zafira service: " + ZAFIRA_URL);
			}
		} 
		catch (Exception e) {
			LOGGER.error("Undefined error during test case/method finish!", e);
		}
	}

	@Override
	public void onTestFailedButWithinSuccessPercentage(ITestResult result)
	{
		if(!ZAFIRA_ENABLED) return;
		
		try 
		{
			Response<TestType> rs = zc.finishTest(populateTestResult(result, Status.FAILED, getFullStackTrace(result)));
			if(rs.getStatus() != 200 && rs.getObject() == null)
			{
				throw new RuntimeException("Unable to register test " + rs.getObject().getName() + " for zafira service: " + ZAFIRA_URL);
			}
		} 
		catch (Exception e) {
			LOGGER.error("Undefined error during test case/method finish!", e);
		}
	}

	@Override
	public void onTestFailure(ITestResult result)
	{
		if(!ZAFIRA_ENABLED) return;
		
		try 
		{
			Response<TestType> rs = zc.finishTest(populateTestResult(result, Status.FAILED, getFullStackTrace(result)));
			if(rs.getStatus() != 200 && rs.getObject() == null)
			{
				throw new RuntimeException("Unable to register test " + rs.getObject().getName() + " for zafira service: " + ZAFIRA_URL);
			}
		} 
		catch (Exception e) {
			LOGGER.error("Undefined error during test case/method finish!", e);
		}
	}

	@Override
	public void onTestSkipped(ITestResult result)
	{
		if(!ZAFIRA_ENABLED) return;
		
		if (result.getThrowable() != null && result.getThrowable().getMessage() != null && result.getThrowable().getMessage().startsWith("ALREADY_PASSED")) 
		{
			return;
		}
		
		try 
		{
			TestType test = testByThread.get(Thread.currentThread().getId());
			
			// When test is skipped as dependent, reinit test from scratch.
			if (test == null) 
			{
				// That's definitely the case with skipped dependent method
				String testName = configurator.getTestName(result);
				
				// If method owner is not specified then try to use suite owner. If both are not declared then ANONYMOUS will be used.
				String owner = !StringUtils.isEmpty(configurator.getOwner(result)) ? configurator.getOwner(result) : configurator.getOwner(result.getTestContext().getSuite());
				UserType methodOwner = zc.registerUser(owner, null, null, null);
				LOGGER.debug("methodOwner: " + methodOwner);
				
				String testClass = result.getMethod().getTestClass().getName();
				String testMethod = configurator.getTestMethodName(result);
				
				//if not start new test as it is skipped dependent test method
				TestCaseType testCase = zc.registerTestCase(this.suite.getId(), methodOwner.getId(), testClass, testMethod);
				String testArgs = result.getParameters().toString();
				
				String group = result.getMethod().getTestClass().getName();
				group = group.substring(0, group.lastIndexOf("."));
				
				test = zc.registerTestStart(testName, group, Status.SKIPPED, testArgs, run.getId(), testCase.getId(), null, null, configurator.getRunCount(result), convertToXML(configurator.getConfiguration()));
			}
			
			Response<TestType> rs = zc.finishTest(populateTestResult(result, Status.SKIPPED, getFullStackTrace(result)));
			if(rs.getStatus() != 200 && rs.getObject() == null)
			{
				throw new RuntimeException("Unable to register test " + rs.getObject().getName() + " for zafira service: " + ZAFIRA_URL);
			}
		} 
		catch (Exception e) {
			LOGGER.error("Undefined error during test case/method finish!", e);
		}
	}
	
	private TestType populateTestResult(ITestResult result, Status status, String message)
	{
		long threadId = Thread.currentThread().getId();
		TestType test = testByThread.get(threadId);
		final Long finishTime = new Date().getTime();
		
		String testName = configurator.getTestName(result);
		LOGGER.debug("testName registered with current thread is: " + testName);
		
		if (test == null) 
		{
			throw new RuntimeException("Unable to find TestType result to mark test as finished! name: '" + testName + "'; threadId: " + threadId);
		}
		
		test.setDemoURL(configurator.getDemoURL(result));
		test.setLogURL(configurator.getLogURL(result));
		test.setTestMetrics(configurator.getTestMetrics(result));
		
		String testDetails = "testId: %d; testCaseId: %d; testRunId: %d; name: %s; thread: %s; status: %s, finishTime: %s \n message: %s";
		String logMessage = String.format(testDetails, test.getId(), test.getTestCaseId(), test.getTestRunId(), test.getName(), threadId, status, finishTime, message);
		
		LOGGER.debug("Test details to finish registration:" + logMessage);

		test.setStatus(status);
		test.setMessage(message);
		test.setFinishTime(finishTime);
		
		testByThread.remove(threadId);
		
		return test;
	}
	
	@Override
	public void onFinish(ISuite suiteContext)
	{
		if(!ZAFIRA_ENABLED) return;
		
		try 
		{
			// Reset configuration to store for example updated at run-time app_version etc
			this.run.setConfigXML(convertToXML(configurator.getConfiguration()));
			zc.registerTestRunResults(this.run);
			zc.sendTestRunReport(this.run.getId(), ZAFIRA_REPORT_EMAILS, false);
		} 
		catch (Exception e) 
		{
			LOGGER.error("Undefined error during test run finish!", e);
		}
	}
	
	
	@Override
	public void onStart(ITestContext context)
	{
		// Do nothing
	}
	
	@Override
	public void onFinish(ITestContext context)
	{
		// Do nothing
	}
	
	//==========================
	
	/**
	 * Reads zafira.properties and creates zafira client.
	 * 
	 * @return if initialization success
	 */
	private boolean initializeZafira()
	{
		boolean success = false;
		try
		{
			CombinedConfiguration config = new CombinedConfiguration(new MergeCombiner());
			config.setThrowExceptionOnMissing(true);
			config.addConfiguration(new SystemConfiguration());
			config.addConfiguration(new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
				    					  .configure(new Parameters().properties().setFileName(ZAFIRA_PROPERTIES)).getConfiguration());

			ci = new CIConfig();
			ci.setCiRunId(config.getString("ci_run_id", UUID.randomUUID().toString()));
			ci.setCiUrl(config.getString("ci_url", "http://localhost:8080/job/unavailable"));
			ci.setCiBuild(config.getString("ci_build", null));
			ci.setCiBuildCause(config.getString("ci_build_cause", "MANUALTRIGGER"));
			ci.setCiParentUrl(config.getString("ci_parent_url", null));
			ci.setCiParentBuild(config.getString("ci_parent_build", null));
			ci.setCiUserId(config.getString("ci_user_id", ANONYMOUS));
			ci.setCiUserFirstName(config.getString("ci_user_first_name", null));
			ci.setCiUserLastName(config.getString("ci_user_last_name", null));
			ci.setCiUserEmail(config.getString("ci_user_email", null));
			
			ci.setGitBranch(config.getString("git_branch", null));
			ci.setGitCommit(config.getString("git_commit", null));
			ci.setGitUrl(config.getString("git_url", null));
			
			JIRA_SUITE_ID = config.getString("jira_suite_id", null);
			
			ZAFIRA_ENABLED = config.getBoolean("zafira_enabled", false);
			ZAFIRA_URL = config.getString("zafira_service_url");
			ZAFIRA_USERNAME = config.getString("zafira_username");
			ZAFIRA_PASSWORD = config.getString("zafira_password");
			ZAFIRA_PROJECT = config.getString("zafira_project");
			ZAFIRA_REPORT_EMAILS = config.getString("zafira_report_emails", "").trim().replaceAll(" ", ",").replaceAll(";", ",");
			ZAFIRA_RERUN_FAILURES = config.getBoolean("zafira_rerun_failures", false);
			ZAFIRA_CONFIGURATOR = config.getString("zafira_configurator", "com.qaprosoft.zafira.listener.DefaultConfigurator");
			
			if(ZAFIRA_ENABLED)
			{
				zc = new ZafiraClient(ZAFIRA_URL, ZAFIRA_USERNAME, ZAFIRA_PASSWORD);
				if(!zc.isAvailable())
				{
					ZAFIRA_ENABLED = false;
					LOGGER.error("Zafira server is unavailable!");
				}
			}
			
			success = ZAFIRA_ENABLED;
		}
		catch(ConfigurationException e)
		{
			LOGGER.error("Unable to locate "+ ZAFIRA_PROPERTIES +": ", e);
		}
		catch(NoSuchElementException e)
		{
			LOGGER.error("Unable to find config property: ", e);
		}
		
		return success;
	}
	
	/**
	 * Marshals configuration bean to XML.
	 * 
	 * @param config bean
	 * @return XML representation of configuration bean
	 * @throws JAXBException
	 */
	private String convertToXML(ConfigurationType config) throws JAXBException
	{
		final StringWriter w = new StringWriter();
		marshaller.marshal(config, w);
		return w.toString();
	}
	
	/**
	 * Generated full test failures stack trace taking into account test skip reasons.
	 * 
	 * @param ITestResult result
	 * @return full error stack trace
	 */
	private String getFullStackTrace(ITestResult result) 
	{
		StringBuilder sb = new StringBuilder();
		
		if(result.getThrowable() == null)
		{
			if(result.getStatus() == ITestResult.SKIP)
			{
				// Identify is it due to the dependent failure or exception in before suite/class/method
				String[] methods = result.getMethod().getMethodsDependedUpon();

				// Find if any parent method failed/skipped
				boolean dependentMethod = false;
				String dependentMethodName = "";
				for (ITestResult failedTest : result.getTestContext().getFailedTests().getAllResults())
				{
					for (int i = 0; i < methods.length; i++)
					{
						if (methods[i].contains(failedTest.getName()))
						{
							dependentMethodName = failedTest.getName();
							dependentMethod = true;
							break;
						}
					}
				}

				for (ITestResult skippedTest : result.getTestContext().getSkippedTests().getAllResults())
				{
					for (int i = 0; i < methods.length; i++)
					{
						if (methods[i].contains(skippedTest.getName()))
						{
							dependentMethodName = skippedTest.getName();
							dependentMethod = true;
							break;
						}
					}
				}

				if (dependentMethod)
				{
					sb.append("Test skipped due to the dependency from: " + dependentMethodName);
				} 
				else 
				{
					//TODO: find a way to transfer configuration failure message in case of error in before suite/class/method
				}
			}
		}
		else
		{
			sb.append(result.getThrowable().getMessage() + "\n");
	    	
            StackTraceElement[] elems = result.getThrowable().getStackTrace();
	        for (StackTraceElement elem : elems) 
	        {
	        	sb.append("\n" + elem.toString());
            }
		}
		
	    return !StringUtils.isEmpty(sb.toString()) ? sb.toString() : null;
	}
}