package org.springframework.experiment.cds.parser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Simple log parser that expects only tags to be specified as decorators of the JVM logs,
 * something like: <pre>
 * -Xlog:cds*:file=yourlog.log:tags
 * </pre>
 *
 * @author María Arias de Reyna
 */
public class LeydenLogParser {

	public static final String RECREATE_MIRROR_FOR_ = "Recreate mirror for ";

	public static final String WITH_CLASS_LOADER = " with class loader: ";

	public static final String RESTORE = "restore: ";

	public static final String HAS_RAW_ARCHIVED_MIRROR = "has raw archived mirror";

	public static final String ERROR = "error";

	public static final String RECREATE = "recreate";

	public static final String RESTORED = "restored";

	public static final String CLEARING_ROOT = "Clearing root ";

	private static final Log logger = LogFactory.getLog(LeydenLogParser.class);

	public LeydenReport parser(Resource resource) throws IOException {
		if (!resource.exists()) {
			throw new IllegalAccessError("Resource " + resource + " does not exist");
		}
		LogLineParser lineParser = new LogLineParser();
		process(resource, lineParser);
		return lineParser.toReport();
	}

	private void process(Resource resource, Consumer<String> line) throws IOException {
		try (Scanner scanner = new Scanner(resource.getInputStream(), StandardCharsets.UTF_8)) {
			while (scanner.hasNextLine()) {
				String nextLine = scanner.nextLine();
				if (StringUtils.hasText(nextLine)) {
					line.accept(nextLine);
				}
			}
		}
	}

	private record Root(String className, String pointer) {

	}

	private class LogLineParser implements Consumer<String> {

		private final Map<String, List<String>> mirror = new HashMap<>();

		private final Map<String, List<String>> unshareable = new HashMap<>();

		private final LinkedList<String> lastMessages = new LinkedList<>();

		private final HashMap<String, Root> roots = new HashMap<>();

		private final LinkedList<String> errors = new LinkedList<>();

		private final HashMap<String, Integer> vtables = new HashMap<>();

		private Integer sccTotalEntries = 0;

		private final HashMap<String, Collection<String>> scc = new HashMap<>();

		private final HashMap<String, Collection<String>> sccNMethod = new HashMap<>();

		public LogLineParser() {
			super();
			this.mirror.put(RECREATE, new LinkedList<>());
			this.mirror.put(RESTORED, new LinkedList<>());
		}

		@Override
		public void accept(String content) {
			LogLine logLine = LogLine.parse(content);
			String message = logLine.message();
			if (logLine.containTags("cds", "vtables")) {
				// [cds,vtables] Copying 14 vtable entries for ConstantPool

				// [cds,vtables] Copying 41 vtable entries for InstanceKlass
				// [cds,vtables] Copying 41 vtable entries for InstanceClassLoaderKlass
				// [cds,vtables] Copying 41 vtable entries for InstanceMirrorKlass
				// [cds,vtables] Copying 41 vtable entries for InstanceRefKlass
				// [cds,vtables] Copying 41 vtable entries for InstanceStackChunkKlass

				// [cds,vtables] Copying 14 vtable entries for Method
				// [cds,vtables] Copying 14 vtable entries for MethodData
				// [cds,vtables] Copying 14 vtable entries for MethodCounters

				// [cds,vtables] Copying 42 vtable entries for ObjArrayKlass
				// [cds,vtables] Copying 42 vtable entries for TypeArrayKlass

				// [cds,vtables] Copying 20 vtable entries for KlassTrainingData
				// [cds,vtables] Copying 20 vtable entries for MethodTrainingData
				// [cds,vtables] Copying 20 vtable entries for CompileTrainingData

				if (message.indexOf("Copying ") == 0) {
					String shortenedMessage = message.substring(7).trim();
					String[] data = shortenedMessage.split(" ");
					if (vtables.containsKey(data[4])) {
						this.errors.add("Repeated vtables message for '" + data[4] + "'. We had value "
								+ vtables.get(data[4]) + " and now we have " + data[0]);
					}
					else {
						vtables.put(data[4], Integer.valueOf(data[0]));
					}
				}

			}
			else if (logLine.containTags("cds", "heap")) {
				if (message.indexOf(CLEARING_ROOT) != -1) {
					String root = message.substring(CLEARING_ROOT.length(), message.indexOf(":"));
					String pointer = message.substring(message.indexOf(":") + 6);
					String previousMessage = this.lastMessages.getFirst();
					if (previousMessage.contains(HAS_RAW_ARCHIVED_MIRROR)) {
						String className = previousMessage.substring(0,
								previousMessage.lastIndexOf(HAS_RAW_ARCHIVED_MIRROR) - 1);
						if (this.roots.containsKey(root)) {
							this.errors.add("Found duplicated root clearing: '" + root + "'. Previous root was "
									+ this.roots.get(root) + ". Current root has className '" + className
									+ "' and pointer '" + pointer + "'.");
						}
						else {
							this.roots.put(root, new Root(className, pointer));
						}
					}
				}
				else if (message.indexOf("Restored ") != -1) {
					// [cds,heap,mirror] Restored
					// java.util.concurrent.locks.AbstractQueuedSynchronizer archived
					// mirror 0x00000007ffe7afc0
					String[] data = message.split(" ");
					String className = data[1];
					String pointer = data[4];
					this.mirror.get(RESTORED).add(className);
					if (!this.roots.containsKey(className)) {
						this.errors.add("Missing restored: '" + className + "'. Current pointer is '" + pointer + "'.");
					}
					else if (!this.roots.get(className).pointer().equals(pointer)) {
						this.errors.add("Mismatched pointer for '" + className + "'. We had "
								+ this.roots.get(className).pointer() + " and now it is " + pointer);
					}
				}
			}
			else if (logLine.containTags("cds", "mirror")) {
				if (message.indexOf(RECREATE_MIRROR_FOR_) != -1) {
					this.mirror.get(RECREATE).add(message.substring(RECREATE_MIRROR_FOR_.length()));
				}
				else if (message.indexOf(HAS_RAW_ARCHIVED_MIRROR) != -1) {
					String className = message.substring(0, message.lastIndexOf(HAS_RAW_ARCHIVED_MIRROR) - 1);
					if (!this.unshareable.values().stream().anyMatch(list -> list.contains(className))) {
						this.errors.add("Weird sequence with '" + className + "'. Got '" + message + "' "
								+ "but no attempt to restore was found previously.");
					}
				}
			}
			else if (logLine.containTags("cds", "unshareable")) {
				if (message.indexOf(RESTORE) != -1) {
					int classLoaderIndex = message.indexOf(WITH_CLASS_LOADER);
					String classLoader = message.substring(classLoaderIndex + WITH_CLASS_LOADER.length());
					if (!this.unshareable.containsKey(classLoader)) {
						this.unshareable.put(classLoader, new LinkedList<>());
					}
					this.unshareable.get(classLoader).add(message.substring(RESTORE.length(), classLoaderIndex));
				}
			}
			else if (logLine.containTags("scc", "init")) {
				if (message.indexOf("entries table at offset") != -1) {
					this.sccTotalEntries = Integer.valueOf(message.split(" ")[1]);
				}
			}
			else if (logLine.containTags("scc")) {
				// [scc,nmethod] 427 (L2): Reading nmethod
				// 'java.lang.Byte::toUnsignedInt(B)I' (decomp: 0, hash: 0xdeef4a7d)
				if (message.indexOf("Reading nmethod") != -1) {
					String[] mess = message.split(" ");
					String level = mess[1].substring(1, mess[1].length() - 2);
					if (!sccNMethod.containsKey(level)) {
						sccNMethod.put(level, new LinkedList<>());
					}
					sccNMethod.get(level).add(mess[4].substring(1));
				}
				else if (message.indexOf(" Shared method lookup") != -1) {
					String[] mess = message.split(" ");
					String level = mess[1].substring(1, mess[1].length() - 2);
					if (!scc.containsKey(level)) {
						scc.put(level, new LinkedList<>());
					}
					scc.get(level).add(mess[5]);
				}
			}

			lastMessages.push(message);
			while (lastMessages.size() > 10) {
				lastMessages.removeLast();
			}
		}

		public LeydenReport toReport() {
			return new LeydenReport(this.mirror, this.unshareable, this.vtables, this.sccTotalEntries, this.scc,
					this.sccNMethod);
		}

	}

}
