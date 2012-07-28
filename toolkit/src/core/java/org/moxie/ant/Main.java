package org.moxie.ant;

import java.util.Properties;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.moxie.Toolkit;
import org.moxie.console.Console;

public class Main extends org.apache.tools.ant.Main {

    /**
     * Command line entry point. This method kicks off the building
     * of a project object and executes a build using either a given
     * target or the default target.
     *
     * @param args Command line arguments. Must not be <code>null</code>.
     */
    public static void main(String[] args) {
        start(args, null, null);
    }
    
    /**
     * Creates a new instance of this class using the
     * arguments specified, gives it any extra user properties which have been
     * specified, and then runs the build using the classloader provided.
     *
     * @param args Command line arguments. Must not be <code>null</code>.
     * @param additionalUserProperties Any extra properties to use in this
     *        build. May be <code>null</code>, which is the equivalent to
     *        passing in an empty set of properties.
     * @param coreLoader Classloader used for core classes. May be
     *        <code>null</code> in which case the system classloader is used.
     */
    public static void start(String[] args, Properties additionalUserProperties,
                             ClassLoader coreLoader) {
        Main m = new Main();
        m.startAnt(args, additionalUserProperties, coreLoader);
    }


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.tools.ant.Main#startAnt(java.lang.String[],
	 * java.util.Properties, java.lang.ClassLoader)
	 */
	@Override
	public void startAnt(String[] args, Properties additionalUserProperties, ClassLoader coreLoader) {
		if (!processArgs(args)) {
			super.startAnt(args, additionalUserProperties, coreLoader);
		}
	}

	private boolean processArgs(String[] args) {
		System.out.println();
		System.out.println("Moxie+Ant v" + Toolkit.getVersion());
		System.out.println(Console.SEP);
		System.out.println();
		return false;
	}
	
	@Override
	protected void addBuildListeners(Project project) {
		super.addBuildListeners(project);
		
		// automatically define Moxie tasks
		Taskdef def = new Taskdef();
		def.setProject(project);
		def.setURI("antlib:org.moxie");
		def.setResource("tasks.properties");
		def.execute();
		
		// automatically add Moxie targets
		// TODO
	}
}
