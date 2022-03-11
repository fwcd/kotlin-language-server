package org.javacs.kt.jdt.ls.extension;

import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.GradleBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.GradleProjectImporter;

/**
 * Custom importer for kotlin gradle projects.
 * This relies on the JDT LS GradleProjectImporter to actually import the base project.
 * It then updates the classpath to include the kotlin compiled classes.
 */
@SuppressWarnings("restriction")
public class KotlinGradleImporter extends GradleProjectImporter  {

	/**
	 * Checks if this is a gradle kotlin project.
	 * For now, we rely on GradleProjectImporter.applies to check if this is a gradle project.
	 * On top of that, we check if we have any kotlin files in the workspace.
	 * If we do, we assume this is a kotlin gradle project.
	 * TODO: We should make this more robust in the future.
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		return super.applies(monitor) && KotlinImporterUtils.anyKotlinFiles(rootFolder.toPath());
	}

	@Override
	public void reset() {
		super.reset();
	}

	/**
	 * This imports the project. It relies on the GradleProjectImporter.importToWorkspace to do most of the work.
	 * Once the base gradle project is imported, we add the kls folder to the classpath as a library.
	 * The kls folder is the folder used by the kotlin language server to output classfiles.
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		super.importToWorkspace(monitor);

		for (IProject project : ProjectUtils.getGradleProjects()) {
			java.nio.file.Path path = Paths.get(project.getLocation().toOSString(), "kls");
			KotlinImporterUtils.setKlsClasspathEntry(project, path, monitor, new GradleBuildSupport());
			KotlinImporterUtils.registerKlsWatcher(path, subMonitor -> {
				try {
					for (IProject gradleProject : ProjectUtils.getGradleProjects()) {
						KotlinImporterUtils.setKlsClasspathEntry(gradleProject, path, subMonitor, new GradleBuildSupport());
					}

					return Status.OK_STATUS;
				} catch (CoreException ex) {
					JavaLanguageServerPlugin.logException(ex);
					return Status.error("An error occurred while updating the kotlin classpath entry");
				}
			});
		}
	}
}
