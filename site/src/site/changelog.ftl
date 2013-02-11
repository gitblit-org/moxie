<h2>${project.version}</h2>
<#if (changelog!?size > 0)>
<table class="table">
	<tbody>				
		<#list changelog as item>
		<tr>
			<td>${item.version}</td><td>${item.date?date}</td><td>${item.note?html}</td>
		</tr>
		</#list>
	</tbody>
</table>
</#if>
