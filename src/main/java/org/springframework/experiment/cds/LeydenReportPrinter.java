package org.springframework.experiment.cds;

import org.springframework.experiment.cds.parser.LeydenLogParser;
import org.springframework.experiment.cds.parser.LeydenReport;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Print statistics of a {@link LeydenReport}.
 *
 * @author Stephane Nicoll
 */
class LeydenReportPrinter {

	void print(LeydenReport report, PrintStream out, Integer listSize) {
		out.println("Leyden Report:");

		out.println("  Which classloaders we are using:");
		report.unshareable()
			.entrySet()
			.forEach((entry) -> out.printf("%10d classes loaded by '%s'  %n", entry.getValue().size(), entry.getKey()));
		out.println();

		out.println("  Mirrored classes:");
		out.printf("%10d classes recreated. %n",
				report.mirror().getOrDefault(LeydenLogParser.RECREATE, Collections.EMPTY_LIST).size());
		out.printf("%10d classes restored. %n",
				report.mirror().getOrDefault(LeydenLogParser.RESTORED, Collections.EMPTY_LIST).size());
		out.println();

		out.println("  Startup Code Cache (SCC):");

		out.printf("%10d entries found in the Startup Code Cache. %n", report.sccTotalEntries());
		out.printf("     Compiled NMethods recovered by compilation level: %n");
		report.sccNMethod()
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.forEach((entry) -> out.printf("%10d entries in compilation level '%s' %n", entry.getValue().size(),
					entry.getKey()));

		for (String level : report.sccNMethod().keySet()) {
			Integer l = Integer.valueOf(level.substring(1));
			for (String level2 : report.sccNMethod().keySet()) {
				Integer l2 = Integer.valueOf(level2.substring(1));
				if (l < l2) {
					List<String> tmp = new ArrayList<>(report.sccNMethod().get(level));
					tmp.retainAll(report.sccNMethod().get(level2));
					for (String className : tmp) {
						out.printf("        > Found %s both in levels %s and %s. %n", className, level, level2);
					}
				}
			}
		}

		HashMap<String, Integer> topClasses = new HashMap<>();
		Integer arrays = 0;
		for (Map.Entry<String, Collection<String>> stringCollectionEntry : report.sccNMethod().entrySet()) {
			for (String value : stringCollectionEntry.getValue()) {
				String className = value.split("::")[0];
				if (className.indexOf("[") > 0) {
					arrays++;
				}
				if (!topClasses.containsKey(className)) {
					topClasses.put(className, 0);
				}
				topClasses.replace(className, topClasses.get(className) + 1);
			}
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

		out.printf("     Top %d classes with more Metadata Methods: %n", listSize);
		topClasses.clear();
		for (Map.Entry<String, Collection<String>> stringCollectionEntry : report.scc().entrySet()) {
			for (String value : stringCollectionEntry.getValue()) {
				String className = value.split("::")[0];
				if (!topClasses.containsKey(className)) {
					topClasses.put(className, 0);
				}
				topClasses.replace(className, topClasses.get(className) + 1);
			}
		}

		topClasses.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByValue((o1, o2) -> o2 - o1))
			.limit(listSize)
			.forEach((className) -> out.printf("%10d methods in class '%s' %n", className.getValue(),
					className.getKey()));
		out.println();

		// out.println(" Vtables information:");
		// report.vtables()
		// .entrySet()
		// .stream()
		// .sorted(Map.Entry.comparingByKey())
		// .forEach((entry) -> out.printf("%10d entries in '%s' %n", entry.getValue(),
		// entry.getKey()));
		// out.println();

		out.println("  Potential Errors:");

		int unshareable = report.unshareable()
			.entrySet()
			.stream()
			.map(stringListEntry -> stringListEntry.getValue().size())
			.reduce((integer, integer2) -> integer + integer2)
			.orElse(0);
		int mirror = report.mirror().get("recreate").size() + report.mirror().get("restored").size();

		if (unshareable != mirror) {
			out.printf("    The number of classes loaded (%d) and mirrored (%d) don't match. %n", unshareable, mirror);
		}

		// if (report.vtables().get("Method") != report.vtables().get("MethodData")) {
		// out.printf(" The number of Method (%d) and MethodData (%d) in vtables don't
		// match. %n",
		// report.vtables().get("Method"), report.vtables().get("MethodData"));
		// }
		// if (report.vtables().get("Method") != report.vtables().get("MethodCounters")) {
		// out.printf(" The number of Method (%d) and MethodCounters (%d) in vtables don't
		// match. %n",
		// report.vtables().get("Method"), report.vtables().get("MethodCounters"));
		// }
		// if (report.vtables().get("ObjArrayKlass") !=
		// report.vtables().get("TypeArrayKlass")) {
		// out.printf(" The number of ObjArrayKlass (%d) and TypeArrayKlass (%d) in
		// vtables don't match. %n",
		// report.vtables().get("ObjArrayKlass"), report.vtables().get("TypeArrayKlass"));
		// }
		// if (report.vtables().get("KlassTrainingData") !=
		// report.vtables().get("MethodTrainingData")) {
		// out.printf(
		// " The number of KlassTrainingData (%d) and MethodTrainingData (%d) in vtables
		// don't match. %n",
		// report.vtables().get("KlassTrainingData"),
		// report.vtables().get("MethodTrainingData"));
		// }
		// if (report.vtables().get("KlassTrainingData") !=
		// report.vtables().get("CompileTrainingData")) {
		// out.printf(
		// " The number of KlassTrainingData (%d) and CompileTrainingData (%d) in vtables
		// don't match. %n",
		// report.vtables().get("KlassTrainingData"),
		// report.vtables().get("CompileTrainingData"));
		// }

		out.printf("%10d Errors found. %n", report.mirror().getOrDefault("error", Collections.EMPTY_LIST).size());
		report.mirror().getOrDefault("error", Collections.EMPTY_LIST).forEach(System.out::println);

		out.println("--------------------------------------------------------------------------");
	}

}
