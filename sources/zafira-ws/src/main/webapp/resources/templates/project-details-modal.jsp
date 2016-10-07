<%@ page 
    language="java"
    contentType="text/html; charset=UTF-8"
    trimDirectiveWhitespaces="true"
    pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/fragments/taglibs.jsp" %>

<div class="modal-header">
	<h3>Project settings <button data-ng-if="project.id" class="btn btn-xs btn-danger" data-ng-really-message="Do you really want to delete project?" data-ng-really-click="deleteProject(project)"> <i class="fa fa-times-circle"></i> delete</button></h3>
</div>
<div class="modal-body">
	<form name="projectForm">
		<div class="form-group">
			<label>Name</label> 
			<input type="text" class="form-control" data-ng-model="project.name" required></input>
		</div>
		<div class="form-group">
			<label>Description</label> 
			<input type="text" class="form-control" data-ng-model="project.description" required></input>
		</div>
	</form>
</div>
<div class="modal-footer">
	<button class="btn btn-success" data-ng-click="createProject(project)"  data-ng-disabled="projectForm.$invalid">
    	Create
    </button>
    <button class="btn btn-primary" data-ng-click="cancel()">
    	Cancel
    </button>
</div>