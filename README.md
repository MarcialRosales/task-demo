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

[Spring Cloud Data Flow](http://docs.spring.io/spring-cloud-dataflow/docs/current-SNAPSHOT/reference/htmlsingle) is a cloud-native orchestration service that allows us to launch standalone tasks or complex data pipelines. This demonstration project will only show how to launch tasks.

You may be wondering why do we need another server to run our tasks. We want to be able to run our tasks in runtime environments other than our local machines. SCDF allows us to run our tasks in Cloud Foundry, Apache YARN, Kubernetes, and other runtimes.

It is worth clarifying that our tasks run in their own runtime, e.g. in case of using *Cloud Foundry*, a task runs in its own container. In the diagram below we can see in our side the SCDF Server and 2 Spring Boot applications, http and cassandra, launched by the SCDF Server and running in their own container. The diagram shows a data pipeline where the *http* application acts as a source of data messages and the *cassandra* as the sink. The two applications are linked thru some messaging middleware like RabbitMQ. The only thing really important right now is the fact that our applications, or tasks, run as standalone applications.
![SCDF Runtime](https://raw.githubusercontent.com/spring-cloud/spring-cloud-dataflow/master/spring-cloud-dataflow-docs/src/main/asciidoc/images/dataflow-server-arch.png)

The role of the SCDF server is to expose an api (REST) that we use to register our applications and tell SCDF to launch them. And SCDF server interacts with the target runtime to deploy them. For convenience, *Spring Cloud Data Flow* comes with a shell application which is the client side of the SCDF server. Whenever we want to register an application or launch it, we start the SCDF shell and submit commands to it as we will see shortly.

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

That is all we need to do to. Now let's see how we can launch it.

### Launch our task in Spring Cloud Data Flow (Local)

Step 1 - Clone [Spring Cloud Data Flow](https://github.com/spring-cloud/spring-cloud-dataflow) repository

Step 2 - Build Project `./mvnw clean install`

Step 3 - Launch ‘Local’ Server [spring-cloud-dataflow-server-local/target]
`java -jar spring-cloud-dataflow-server-local/target/spring-cloud-dataflow-server-local-[VERSION].jar`

Step 4 - Launch Shell [spring-cloud-dataflow-shell/target]
`java -jar spring-cloud-dataflow-shell/target/spring-cloud-dataflow-shell-[VERSION].jar`

Launching a task in SCDF requires 3 steps:
- Register our application's code with the SCDF so that it can execute it. There are various ways to register our application's code. The most intuitive one is to register the jar we just built.
- Create the task definition that links our application's code with a logical name.
- Launch the task with any required parameters, e.g. our greetings message or the exit status.

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

### Launch our task in Spring Cloud Data Flow (Cloud Foundry)

In the previous section, we downloaded from Github the source code of SCDF and we built it. This time we are going to create a project for the SCDF and another for the shell. We will discuss later on why this option is far more interesting than using the canned version.

Step 1 - Create our *Data Flow Server* (scdf-server).
This will be a Spring Boot application like this one below:
```
@SpringBootApplication
@EnableDataFlowServer
public class ScdfServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScdfServerApplication.class, args);
	}
}
```

Step 2 - Create our *Data Flow Shell* (scdf-shell)..
This will be a Spring Boot Application like this one below:
```

@SpringBootApplication
@EnableDataFlowShell
public class ScdfShellApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScdfShellApplication.class, args);
	}
}
```

Step 3 - Deploy *Data Flow Server* to *Cloud Foundry*
Before we deploy the SCDF server we need to provision the `task-repository` service, i.e. the database we want to use to track the tasks. If we open the `manifest.yml` (src/resources/manifest.yml) we will see that it needs it:
```
---
applications:
- name: scdf-server
  host: scdf-server
  path: '@project.build.finalName@.jar'
  memory: 1G
  disk_quota: 2G
  services:
   - task-repository
```

Let's provision a mysql database thru *Cloud Foundry's marketplace*.
```
cf create-service p_mysql 100mb task-repository
```

Now, we can build and push the SCDF server by calling the following command from `scdf-server` folder:
```
mvn install
cf push
```

Step 4 - Run *Data Flow Shell* locally against our *Data Flow Server* running in *Cloud Foundry*
We are going to follow practically the same steps we did when we run SCDF locally, except that we need to configure the shell with the address where the SCDF Server is.
```
mvn spring-boot:run
```
```
server-unknown:>dataflow config server http://scdf-server.cfapps.io
Successfully targeted http://scdf-server.cfapps.io
dataflow:>
```
