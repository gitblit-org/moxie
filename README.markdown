## Maxilla Project Build Toolkit

Maxilla is a small collection of ANT tasks to facilitate building Java projects.

**This is not production-ready!**

### Tasks <small>What can it do?</small>

#### MaxSetup

* Dependency retrieval from local Maven repository or content-addressable remote Maven repositories like MavenCentral.
* Eclipse .classpath generation

#### MaxExtract

Pull the contents of a field from a source file.  
This is useful for pulling a version identifier from source that you want to use in your build script.

#### MaxCommitId

Determines the Git commit hash of HEAD for the specified path.

#### MaxGhPages

Updates the gh-pages branch of a Git repository with content from the specified folder.

#### MaxJar

This is a fork of the super-useful GenJar.

#### MaxDoc

Generates a project site or project documentation from a document definition and markdown source files.

* Uses Twitter Bootstrap 2.0 for the generated content
* Optionally injects GoogleCode Prettify for syntax highlighting
* Optionally injects FancyBox for JavaScript-enhanced image gallery viewing

### Builds <small>Maxilla is available in two builds: core and all</small>

#### maxilla-core.jar (45KB)

[Maxilla Core](./maxilla-core.jar) includes MaxSetup, MaxExtract, and MaxCommitId.

#### maxilla-all.jar (275KB)

[Maxilla All](./maxilla-all.jar) includes all the Maxilla tasks, their dependencies, and resources.

### License

Maxilla is distributed under the Apache Software License, version 2.0.