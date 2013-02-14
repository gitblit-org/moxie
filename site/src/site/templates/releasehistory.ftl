<#include "macros.ftl" >

<!-- HISTORY -->
<#if (all!?size > 0)>
	<p></p>
	<h2>All Releases</h2>
	<table class="table">
		<tbody>
		<!-- RELEASE HISTORY -->
		<#list all?sort_by("date")?reverse as log>
		<tr id="${log.id}">
			<td style="width:100px" id="${log.id}">
				<b><a href="#${log.id}">${log.id}</a></b><br/>
				${log.date?date?string("yyyy-MM-dd")}
			</td>
			<td><@LogDescriptionMacro log=log /></td>
		</tr>
		</#list>
		</tbody>
	</table>
</#if>