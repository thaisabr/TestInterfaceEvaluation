Test Interface Evaluation
==========

Groovy code to support an evaluation study for a test-based approach to compute task interface.

Such an approach deals with GitHub repositories using Gherkin-based acceptance test tool (like <b>Cucumber</b>) that maintain a link among tasks and code changes. Such a link is defined by the occurrence of task ID in commit messages. That is, if it possible to identify changes at the production code and at the test code related to a same task, which in turn is identified by an ID.
The task is a programming activity, such as the development of a new feature, a feature change, bug fix or refactoring.

Mechanism
-
The program takes as input a csv file that contains tasks from GitHub repositories (generated previously by `MiningGit - https://github.com/thaisabr/mining_git`).
Each task contains <b>index</b>, <b>repository URL</b>, <b>ID</b>, <b>commits hash</b>, <b>changed production files</b>, <b>changed test files</b> and <b>commits message</b>. See an example of task file in <b>/input/tasks.csv</b>.
The program downloads each repository (/repositories) and analyses its commit history to identify acceptance test(s) related to each task. An acceptance test is identified by its Gherkin file and line (position of the scenario's title). Such a information is needed to compute test-based task interfaces.
The evaluation study compares test-based task interfaces and real task interfaces (production code changes).

More about Gherkin and Cucumber: https://github.com/cucumber/cucumber/wiki/Gherkin.

Compilation
-
This project uses Apache Maven to manage all dependencies and versioning. 

More about Maven: https://maven.apache.org/

Configuration (configuration.properties file)
-
`spgroup.task.file.path`:  Task file's path. By default, its value is <b>tasks.csv</b>. 
`spgroup.task.repositories.path`: Repositories folder's path. By default, its value is <b>repositories</b>.
`spgroup.task.interface.path.test`: Test code's path. By default, its value is the common one used in Cucumber and RSpec projects.
`spgroup.task.interface.path.toignore`: Files or folders to ignore when classifying production and test code.

Execution
-
(1) Generate the jar file (TestInterfaceEvaluation-1.0-SNAPSHOT-jar-with-dependencies.jar) by using Maven

(2) Locate the jar and the `configuration.properties` file at target folder

(3) Configure the properties file

(4) Run the jar by command line: `java -jar TestInterfaceEvaluation-1.0-SNAPSHOT-jar-with-dependencies.jar`


To remember: The jar and the properties file must be at the same folder.