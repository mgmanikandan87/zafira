package com.qaprosoft.zafira.tests.gui.components;

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import com.qaprosoft.zafira.tests.gui.AbstractUIObject;
import com.qaprosoft.zafira.tests.gui.pages.DashboardPage;

public class DashboardTabMenu extends AbstractUIObject
{

	@FindBy(xpath = ".//a")
	private List<WebElement> dashboardButtons;

	public DashboardTabMenu(WebDriver driver, SearchContext context)
	{
		super(driver, context);
	}

	public List<String> getDashboardNames()
	{
		return dashboardButtons.stream().map(WebElement::getText).collect(Collectors.toList());
	}

	public WebElement getDashboardByName(String name)
	{
		return context.findElements(By.xpath(".//a")).stream()
				.filter(webElement -> webElement.getText().equals(name)).findFirst().orElse(null);
	}

	public DashboardPage clickDashboardByName(String name)
	{
		getDashboardByName(name).click();
		return new DashboardPage(driver);
	}

	public boolean isProjectIsHidden(String name)
	{
		return isElementPresent(getDashboardByName(name), By.xpath("./i[contains(@class, 'fa')]"), 1);
	}
}
