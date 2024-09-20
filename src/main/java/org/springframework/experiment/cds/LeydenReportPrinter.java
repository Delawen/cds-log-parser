package org.springframework.experiment.cds;

import org.springframework.experiment.cds.parser.LeydenLogParser;
import org.springframework.experiment.cds.parser.LeydenReport;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Print statistics of a {@link LeydenReport}.
 *
 * @author Stephane Nicoll
 */
class LeydenReportPrinter {

	void print(LeydenReport report, PrintStream out, Integer listSize) {
		out.println("Leyden Report:");

		out.println(" CLASSES:");

		out.println("  Used classloaders:");
		report.unshareable()
			.entrySet()
			.forEach((entry) -> out.printf("%10d classes loaded by '%s'  %n", entry.getValue().size(), entry.getKey()));
		out.println();

		out.println(" METADATA SPACE:");
		HashMap<String, Integer> topClasses = new HashMap<>();
		Set<String> uniqueMethods = new HashSet<>();
		for (Map.Entry<String, Collection<String>> stringCollectionEntry : report.scc().entrySet()) {
			uniqueMethods.addAll(stringCollectionEntry.getValue());
		}
		for (String value : uniqueMethods) {
			String className = value.split("::")[0];
			if (!topClasses.containsKey(className)) {
				topClasses.put(className, 0);
			}
			topClasses.replace(className, topClasses.get(className) + 1);
		}
		out.printf("%10d Unique Metadata Methods found. %n", uniqueMethods.size());
		out.printf("     Top %d classes with more Metadata Methods: %n", listSize);
		topClasses.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue((o1, o2) -> o2 - o1))
			.limit(listSize)
			.forEach((className) -> out.printf("%10d metadata methods in class '%s' %n", className.getValue(),
					className.getKey()));
		out.println();

		out.println(" HEAP OBJECTS:");
		out.printf("%10d classes recreated. %n",
				report.mirror().getOrDefault(LeydenLogParser.RECREATE, Collections.EMPTY_LIST).size());
		out.printf("%10d classes restored. %n",
				report.mirror().getOrDefault(LeydenLogParser.RESTORED, Collections.EMPTY_LIST).size());
		int unshareable = report.unshareable()
			.entrySet()
			.stream()
			.map(stringListEntry -> stringListEntry.getValue().size())
			.reduce((integer, integer2) -> integer + integer2)
			.orElse(0);
		int mirror = report.mirror().get("recreate").size() + report.mirror().get("restored").size();

		if (unshareable != mirror) {
			out.printf("Warning: The number of classes loaded (%d) and mirrored (%d) don't match. %n", unshareable,
					mirror);
		}
		out.printf("%10d Errors found. %n", report.errorsHeapObjects().size());
		report.errorsHeapObjects().forEach(System.out::println);
		out.println();

		out.println(" NMETHODS");
		out.printf("%10d total entries found in the Startup Code Cache (SCC). %n", report.sccTotalEntries());
		out.printf("     Compiled NMethods recovered by compilation level: %n");
		report.sccNMethod()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach((entry) -> out.printf("%10d entries in compilation level '%s' %n", entry.getValue().size(),
					entry.getKey()));

		Set<String> uniqueNMethods = new HashSet<>();
		Integer totalNMethods = 0;
		for (String level : report.sccNMethod().keySet()) {
			Integer l = Integer.valueOf(level.substring(1));
			final var sccNMethodsOnLevel = report.sccNMethod().get(level);
			uniqueNMethods.addAll(sccNMethodsOnLevel);
			totalNMethods += sccNMethodsOnLevel.size();
			for (String level2 : report.sccNMethod().keySet()) {
				Integer l2 = Integer.valueOf(level2.substring(1));
				if (l < l2) {
					List<String> tmp = new ArrayList<>(sccNMethodsOnLevel);
					tmp.retainAll(report.sccNMethod().get(level2));
					for (String className : tmp) {
						out.printf("        > Found %s both in levels %s and %s. %n", className, level, level2);
					}
				}
			}
		}

		out.printf("%10d NMethods restored across all levels%n", totalNMethods);
		out.printf("%10d Unique NMethods in total%n", uniqueNMethods.size());

		topClasses.clear();
		Integer arrays = 0;
		for (String value : uniqueNMethods) {
			String className = value.split("::")[0];
			if (className.indexOf("[") > 0) {
				arrays++;
			}
			if (!topClasses.containsKey(className)) {
				topClasses.put(className, 0);
			}
			topClasses.replace(className, topClasses.get(className) + 1);
		}

		out.printf("     From which %d classes are arrays. %n", arrays);
		out.println();
		out.printf("     Top %d classes with compiled nmethods: %n", listSize);

		topClasses.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue((o1, o2) -> o2 - o1))
			.limit(listSize)
			.forEach((className) -> out.printf("%10d Total NMethods in class '%s' %n", className.getValue(),
					className.getKey()));

		out.printf("%10d Errors found. %n", report.errorsVtables().size());
		report.errorsVtables().forEach(System.out::println);

		out.println("--------------------------------------------------------------------------");
	}

}
