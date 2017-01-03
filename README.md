# Spring Cloud Task Demonstration Project

This project demonstrates how to execute tasks, a.k.a. one-off jobs, in a cloud platform like *Pivotal Cloud Foundry*. Unlike applications, a task typically executes for a limited amount of time and it does not expose any ports.

There are 2 ways to execute tasks: Directly in the platform or via Spring Cloud Data Flow.

1. [Launch Task natively in PCF](#launch-task-natively-in-pcf)
2. [Launch task via Spring Cloud Data Flow](#launch-task-via-spring-cloud-data-flow)

## Launch task natively in PCF

*Cloud Foundry* allows us to execute `Tasks` which are one-off jobs that are intended to perform a task, stop, and be cleaned up, freeing up resources.

Our task is a simple command-line Spring Boot application (`task-sample`) which we can launch it locally.
```
$ cd task-sample
$ mvn install
$ java -jar target/task-sample-0.0.1-SNAPSHOT.jar --helloworld.greeting=Joe --helloworld.taskLengthSec=3
```
And it produces a log statement like this one:
```
[null] hello world Joe  [3]
```

Let's execute our task in *Cloud Foundry*. First we need to push the task. Because our task is a command-line application it does not listen on any ports, hence we don't need a route (`--no-route`) neither a health check (`-u none`) and we don't want to start it when we push it (`--no-start`):
```
$ cf push task-sample --no-route -u none --no-start -p target/task-sample-0.0.1-SNAPSHOT.jar
```

We can check our task is ready in *Cloud Foundry* :
```
$ cf apps
Getting apps in org pivot-mrosales / space development as mrosales@pivotal.io...
OK

name               requested state   instances   memory   disk   urls
task-sample        stopped           0/1         1G       1G
```

Now we can execute our task:
```
$ ./runTask.sh task-sample
```
Produces:
```
Executing task-sample with GUI e4917000-fbde-45e7-8e9a-313af9ac9ef0
Executing command
task-sample "RUNNING" ("93687781-9cd1-48c7-8910-1a06e4c3b91e")
```

We can check out the status of our task:
```
$ ./checkTask.sh 93687781-9cd1-48c7-8910-1a06e4c3b91e
```
Produces:
```
Checking task 93687781-9cd1-48c7-8910-1a06e4c3b91e
"SUCCEEDED"
```

We can launch it as many times as we want. We can get the list of executed tasks.
```
cf curl /v3/apps/$APP_GUID/tasks
```


## Launch task via Spring Cloud Data Flow

[Spring Cloud Data Flow](http://docs.spring.io/spring-cloud-dataflow/docs/current-SNAPSHOT/reference/htmlsingle) is a cloud-native orchestration service that allows us to launch standalone tasks or complex data pipelines. This demonstration project will only show how to launch tasks and track them.

You may be wondering why do we need another server to run our tasks. We want to be able to run our tasks in other runtime environments other than our local machines. SCDF allows us to run our tasks in Cloud Foundry, Apache YARN, Kubernetes, among other runtimes.

It is worth clarifying that our tasks run in their own runtime, e.g. in case of using *Cloud Foundry*, a task runs in its container. SCDF provides a set of services that run parallel to our tasks and they are:
- SCDF Server : Orchestrates task execution. It deploys tasks to the runtime, e.g. *PCF*
- SCDF Shell : Command-line application that allows us to interact with the SCDF Server
- SCDF Admin : Web-front end application that allows us to interact with the SCDF Server
![SCDF Runtime](https://raw.githubusercontent.com/spring-cloud/spring-cloud-dataflow/master/spring-cloud-dataflow-docs/src/main/asciidoc/images/dataflow-server-arch.png)

### Update our task-sample project

We are going to use our `task-sample` project but we need to make some changes so that it can be launched as an *SCDF Task*.

First we add a new dependency to the pom.xml.
```
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-task-starter</artifactId>
  <version>1.0.2.RELEASE</version>
</dependency>
```

And second, we add a new annotation, `@EnableTask`. This annotation makes this application `suitable` to run within Spring Cloud Data Flow server as a **Task**.
```
@SpringBootApplication
@EnableTask
public class TaskSampleApplication {
```

That is all we need to do to run our task in SCDF.

### Launch our task in Spring Cloud Data Flow

Step 1 - Clone [Spring Cloud Data Flow](https://github.com/spring-cloud/spring-cloud-dataflow) repository

Step 2 - Build Project `./mvnw clean install`

Step 3 - Launch ‘Local’ Server [spring-cloud-dataflow-server-local/target]
`java -jar spring-cloud-dataflow-server-local/target/spring-cloud-dataflow-server-local-[VERSION].jar`

Step 4 - Launch Shell [spring-cloud-dataflow-shell/target]
`java -jar spring-cloud-dataflow-shell/target/spring-cloud-dataflow-shell-[VERSION].jar`

Launching a task in Spring Cloud Data Flow, SCDF from now on, requires 3 steps:
1. Register our application's code with the SCDF so that it can execute it. There are various ways to register our application's code. The most intuitive one is to register the jar we just built.
2. Create the task definition that links our application's code with a logical name.
3. Launch the task with any required parameters, e.g. our greetings message or the exit status.

Step 5 - Register our application's code via the SCDF's shell
```
dataflow:>app register --name task-sample --type task --uri file:///Users/mrosales/Documents/task-demo/task-sample/target/task-sample-0.0.1-SNAPSHOT.jar
```

Step 6 - Verify your task application has been registered by using the `app list` command
```
dataflow:>app list
```

Step 7 - Create a task definition
```
dataflow:>task create hello-world --definition "task-sample"
```

Step 8 - Verify your task has been created by visiting `http://localhost:9393/dashboard/index.html#/tasks/definitions` or by running `task list` command in the SCDF's Shell.


Step 9 - Launch the `hello-world` task with the greeting `Bob` and takes 10 seconds to execute
```
dataflow:>task launch hello-world --arguments "--helloworld.greeting=Bob,--helloworld.taskLengthSec=10"
```

Step 10 - Verify the task's status is `Completed` (this is regardless of the exit status)
```
task list
```
Note: We cannot use the command `task execution list` to check out the task's exit code or its start and end time because by default if we use H2 database for the TaskRepository, Spring Cloud Task does not record the executed tasks in the db.


## Launch a pipeline where every http request is sent to a log file

SCDF comes with lots of *modules* that we can use to build our pipelines. We don't need to implement every task that makes up our pipelines. What are those *modules*? They are defined here http://cloud.spring.io/spring-cloud-stream-app-starters/ and they can be of 3 types: source, processor and sink. A *source module* is an application that produces data. A *processor module* is an application which receives data, transforms it and maybe produces a new data. A *sink module* is an application that consumes data. Modules are linked together via a messaging middleware. There 2 types: RabbitMQ and Kafka.

We are not going to implement a `http` task or a `log` task because SCDF comes with these tasks. If we execute `app list` in the SCDF shell we wont see these apps because we have not imported them. Let's create a file where we indicate we want to register the modules we are interested in.

Lets edit our file that we are going to call it http-log-module-descriptors.properties
```
source.http=maven://org.springframework.cloud.stream.app:http-source-rabbit:1.0.4.RELEASE
sink.log=maven://org.springframework.cloud.stream.app:log-sink-rabbit:1.0.4.RELEASE
```
and let's import these modules:
```
dataflow:>app import --uri file:///Users/mrosales/Documents/task-demo/http-log-module-descriptors.properties
```
Produces:
```
Successfully registered applications: [sink.log, source.http]
```

If you want to load all the modules call this command instead:
```
dataflow:>app import --uri http://bit.ly/1-0-4-GA-stream-applications-rabbit-maven
```
