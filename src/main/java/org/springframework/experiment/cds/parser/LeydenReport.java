package org.springframework.experiment.cds.parser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Report of Leyden related statistics
 *
 * @author María Arias de Reyna
 */
public record LeydenReport(Map<String, List<String>> mirror, Map<String, List<String>> unshareable,
		Map<String, Integer> vtables, Integer sccTotalEntries, Map<String, Collection<String>> scc,
		Map<String, Collection<String>> sccNMethod, List<String> errorsHeapObjects, List<String> errorsVtables) {

}