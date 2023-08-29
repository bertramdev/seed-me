<h2>Seed Admin</h2>
<h4>Actions</h4>
<ul>
	<li><g:link controller="adminSeed" action="install">Process Seed</g:link></li>
</ul>
<g:form name="seed" controller="adminSeed" action="process" class="form">
	<fieldset>
		<legend>Upload Seed File</legend>
		<label>Seed Data</label>
		<g:textArea name="seedFile" value="" rows="5"></g:textArea>
		<label>&nbsp;</label>
		<g:submitButton name="submit" value="submit" class="btn btn-primary"/>
	</fieldset>
	<div>
		<g:if test="${flash.message}">
			<p class="text-success" style="color: green;">${flash.message}</p>
		</g:if>
		<g:if test="${flash.error}">
			<p class="text-danger" style="color: red;">${flash.error}</p>
		</g:if>
	</div>
</g:form>
