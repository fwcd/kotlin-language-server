package org.javacs.kt.jdt.ls.extension;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.MavenBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.MavenProjectImporter;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

/**
 * Custom importer for kotlin maven projects.
 * This relies on the JDT LS MavenProjectImporter to actually import the base project.
 * It then updates the classpath to include the kotlin compiled classes.
 */
@SuppressWarnings("restriction")
public class KotlinMavenImporter extends MavenProjectImporter  {

	/**
	 * Checks if this is a maven kotlin project.
	 * For now, we rely on MavenProjectImporter.applies to check if this is a maven project.
	 * On top of that, we check if we have any kotlin files in the workspace.
	 * If we do, we assume this is a kotlin maven project.
	 * TODO: We should make this more robust in the future.
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		if (super.applies(monitor)) {
			try (Stream<java.nio.file.Path> files = Files.walk(rootFolder.toPath())) {
				return files.anyMatch(f -> f.getFileName().toString().endsWith(".kt"));
			} catch (IOException ex) {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public void reset() {
		super.reset();
	}

	/**
	 * This imports the project. It relies on the MavenProjectImporter.importToWorkspace to do most of the work.
	 * Once the base maven project is imported, we add the kls folder to the classpath as a library.
	 * The kls folder is the folder used by the kotlin language server to output classfiles.
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		super.importToWorkspace(monitor);

		for (IMavenProjectFacade mavenProject : MavenPlugin.getMavenProjectRegistry().getProjects()) {
			IProject project = mavenProject.getProject();
			java.nio.file.Path path = Paths.get(project.getLocation().toOSString(), "kls");
			setKlsClasspathEntry(project, path, monitor);
			registerKlsWatcher(path);
		}
	}

	/**
	 * Registers a watcher for the kls directory.
	 * Any file change leads to an update in the classpath.
	 */
	private void registerKlsWatcher(java.nio.file.Path path) {
		new Thread(() -> {
			try {
				Files.createDirectories(path);
				Map<WatchKey, java.nio.file.Path> keys = new HashMap<>();
				// Create the watcher and watch all directories under the base kls directory.
				WatchService watcher = FileSystems.getDefault().newWatchService();
				keys.putAll(watchDirectory(watcher, path));

				// Wair for file related events.
				// When a file is added or deleted, we update the classpath (we remove and re-add the kls classpath entry)
				while (true) {
					WatchKey key;
					if ((key = watcher.take()) != null) {
						java.nio.file.Path dir = keys.get(key);
						if (dir != null) {
							key.pollEvents().forEach(event -> {
								@SuppressWarnings("unchecked")
								WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;

								java.nio.file.Path name = ev.context();
								java.nio.file.Path child = dir.resolve(name);

								// If a new directory was created, we need to watch it as well.
								if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
									if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
										keys.putAll(watchDirectory(watcher, dir));
									}
								}
							});
							Job job = new Job("Updating kotlin classpath entry") {
								@Override
								protected IStatus run(IProgressMonitor monitor) {
									try {
										for (IMavenProjectFacade mavenProject : MavenPlugin.getMavenProjectRegistry().getProjects()) {
											IProject project = mavenProject.getProject();
											setKlsClasspathEntry(project, path, monitor);
										}
		
										return Status.OK_STATUS;
									} catch (CoreException ex) {
										JavaLanguageServerPlugin.logException(ex);
										return Status.error("An error occurred while updating the kotlin classpath entry");
									}
								}
							};
							job.schedule();
							job.join();
							key.reset();
						}
					}
				}
			} catch (IOException | InterruptedException ex) {
				JavaLanguageServerPlugin.logException(ex);
			}
		}).start();
	}

	private Map<WatchKey, java.nio.file.Path> watchDirectory(WatchService watcher, java.nio.file.Path path) {
		Map<WatchKey, java.nio.file.Path> keys = new HashMap<>();

		try {
			Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {
				@Override
				public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs) throws IOException {
					WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
					keys.put(key, dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ex) {
			JavaLanguageServerPlugin.logException(ex);
		}

		return keys;
	}

	private void setKlsClasspathEntry(IProject project, java.nio.file.Path path, IProgressMonitor monitor) throws CoreException {
		IJavaProject javaProject = JavaCore.create(project);

		List<IClasspathEntry> entries = new ArrayList<>(Arrays.asList(javaProject.getRawClasspath()));
		IPath klsPath = Path.fromOSString(path.toString());

		entries.removeIf(entry -> entry.getPath().equals(klsPath));
		IClasspathEntry classpathEntry = JavaCore.newLibraryEntry(klsPath, null, null);
		entries.add(classpathEntry);

		javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[0]), monitor);
		new MavenBuildSupport().refresh(project, CHANGE_TYPE.CHANGED, monitor);
	}
}
