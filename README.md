# Spring Cloud Task Demonstration Project

This project demonstrates how to execute tasks, a.k.a. one-off jobs, in a cloud platform like *Pivotal Cloud Foundry*. Unlike applications, a task typically executes for a limited amount of time and it does not expose any ports.

There are 2 ways to execute tasks: Directly in the platform or via Spring Cloud Data Flow.

1. [Launch Task natively in PCF](#launch-task-natively-in-pcf)
2. [Launch task via Spring Cloud Data Flow (Local)](#launch-our-task-in-spring-cloud-data-flow-local)
2. [Launch task via Spring Cloud Data Flow (Cloud Foundry)](#launch-our-task-in-spring-cloud-data-flow-cloud-foundry)

## Launch task natively in PCF

*Cloud Foundry* allows us to execute `Tasks` which are one-off jobs that are intended to perform a task, stop, and be cleaned up, freeing up resources:
- A task is a command run in the context of an app, therefore a task is an instantiation of an application previously deployed in PCF (a process run against a droplet).
- A task is only ever run at most once
- A task can either fail or succeed
- A task includes the command to start the process, disk size, and memory allocation
- A task inherits environment variables, service bindings and security groups bound to the application
- A task is a single-use object which can be checked for state and success/failure message
- To re-execute a task, a new task must be created
- A task is cancelable
- A syslog drain attached to an app will receive task log output
- Stdout/stderr from the task will be available on the app’s firehose logs
- Tasks are always executed asynchronously
- Tasks respect app, space, and organization level memory quotas
- Task execution history is retained for one month

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

Let's execute our task in *Cloud Foundry*. First we need to push the task's code, or application. Because our task is a command-line application it does not listen on any ports, hence we don't need a route (`--no-route`) neither a health check (`-u none`) and we don't want to start it when we push it (`--no-start`):
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

Now we can execute our task. We will use the [v3 REST api of *Cloud Foundry*](http://v3-apidocs.cloudfoundry.org/version/3.0.0/index.html#tasks) however we can equally run tasks via the [CF CLI](https://docs.cloudfoundry.org/devguide/using-tasks.html#task-proceses).

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

**Step 1 - Clone [Spring Cloud Data Flow](https://github.com/spring-cloud/spring-cloud-dataflow) repository**

**Step 2 - Build Project `./mvnw clean install`**

**Step 3 - Launch ‘Local’ Server [spring-cloud-dataflow-server-local/target]**
`jjava -jar spring-cloud-dataflow-server-local/target/spring-cloud-dataflow-server-local-[VERSION].jar`

**Step 4 - Launch Shell [spring-cloud-dataflow-shell/target]**
`java -jar spring-cloud-dataflow-shell/target/spring-cloud-dataflow-shell-[VERSION].jar`

Launching a task in SCDF requires 3 steps:
- Register our application's code with the SCDF so that it can execute it. There are various ways to register our application's code. The most intuitive one is to register the jar we just built.
- Create the task definition that links our application's code with a logical name.
- Launch the task with any required parameters, e.g. our greetings message or the exit status.

**Step 5 - Register our application's code via the SCDF's shell**
```
dataflow:>app register --name task-sample --type task --uri file:///Users/mrosales/Documents/task-demo/task-sample/target/task-sample-0.0.1-SNAPSHOT.jar
```

**Step 6 - Verify your task application has been registered by using the `app list` command**
```
dataflow:>app list
```

**Step 7 - Create a task definition**
```
dataflow:>task create hello-world --definition "task-sample"
```

**Step 8 - Verify your task has been created by visiting `http://localhost:9393/dashboard/index.html#/tasks/definitions` or by running `task list` command in the SCDF's Shell.**


**Step 9 - Launch the `hello-world` task with the greeting `Bob` and takes 10 seconds to execute**
```
dataflow:>task launch hello-world --arguments "--helloworld.greeting=Bob,--helloworld.taskLengthSec=10"
```

**Step 10 - Verify the task's status is `Completed` (this is regardless of the exit status)**
```
task list
```
Note: We cannot use the command `task execution list` to check out the task's exit code or its start and end time because by default if we use H2 database for the TaskRepository, Spring Cloud Task does not record the executed tasks in the db.

### Launch our task in Spring Cloud Data Flow (Cloud Foundry)

In the previous section, we run SCDF server locally. In this section, we are going to run it in PCF. Also, in the previous section, we downloaded from Github the source code of SCDF and we built it. This time we are going to download a SCDF server built specifically to be deployed onto *Cloud Foundry*.

**Step 1 - Download Spring Cloud Data Flow for Cloud Foundry**

```
wget http://repo.spring.io/snapshot/org/springframework/cloud/spring-cloud-dataflow-server-cloudfoundry/1.1.0.BUILD-SNAPSHOT/spring-cloud-dataflow-server-cloudfoundry-1.1.0.BUILD-SNAPSHOT.jar
```

**Step 2 - Deploy Spring Cloud Data Flow on Cloud Foundry**

Before we deploy the SCDF server we need to provision the `task-repository` service, i.e. the database we want to use to track the tasks. We also need to provision a Redis instance used by SCDF to track analytics.

Let's provision the `task-repository` service as a `mysql` database thru *Cloud Foundry's marketplace*.

```
$> cf create-service cleardb spark scdf-task-repo
$> cf create-service rediscloud 100mb scdf-analytics

```

Now, we can push the SCDF server:
```
cf push scdf-server-mr -m 2G -k 2G --no-start -p spring-cloud-dataflow-server-cloudfoundry-1.1.0.BUILD-SNAPSHOT.jar
```

We bind the services to the SCDF server:
```
cf bind-service scdf-server scdf-task-repo
cf bind-service scdf-server scdf-analytics
```

And we configure the SCDF server. These settings tell the SCDF Server where to deploy the tasks.
```
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL https://api.run.pivotal.io
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG pivotal-emea-cso
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE mrosales
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN cfapps.io
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_TASK_SERVICES scdf-task-repo
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME <put here your username>
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD <put here your password>
cf set-env scdf-server-mr SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION false
```

Now we can start the SCDF server:
```
cf start scdf-server
```

**Step 3 - Launch Shell [spring-cloud-dataflow-shell/target]**

We use the SCDF Shell to interact with the SCDF server. We need to configure it to point to the newly deployed SCDF Server in *Cloud Foundry*.

```
java -jar spring-cloud-dataflow-shell/target/spring-cloud-dataflow-shell-[VERSION].jar

server-unknown:>dataflow config server http://scdf-server.cfapps.io
Successfully targeted http://scdf-server.cfapps.io
dataflow:>

```

**Step 4 - Register the application and launch it**

When we ran the SCDF locally we could import the application directly from the local file system. When we run it in *Cloud Foundry* we need to use other mechanisms. We can either specify a http or maven `uri`. We have built and released our task-sample as a release in Github and it is available in this url: https://github.com/MarcialRosales/task-demo/releases/download/v1.0/task-sample-0.0.1-SNAPSHOT.jar. If we use *Artifactory* as our corporate *Maven* repository, we would have to configure SCDF Server with the location of our Artifactory (`cf set-env scdf-server MAVEN_REMOTE_REPOSITORIES_REPO1_URL https://artifactory/libs-snapshot`) and the URI following this pattern `maven://com.example:task-sample:0.0.1-SNAPSHOT`.

```
dataflow:>app import --uri https://github.com/MarcialRosales/task-demo/releases/download/v1.0/task-sample-0.0.1-SNAPSHOT.jar
dataflow:>task create hello-world --definition "task-sample"
dataflow:>task launch hello-world --arguments "--helloworld.greeting=Bob"
```

Once we launch it we can check that *Cloud Foundry* has deployed our application `task-sample`.
```
$ cf apps
Getting apps in org pivotal-emea-cso / space mrosales as mrosales@pivotal.io...
OK

name             requested state   instances   memory   disk   urls
hello-world     stopped           0/1         1G       1G
scdf-server-mr     started           1/1         2G       2G     scdf-server-mr.cfapps.io
```

Also, we can check the logs of our task `hello-world`:
```
$ cf logs hello-world
....
2017-01-05T15:41:04.91+0100 [APP/TASK/hello-world4/0]OUT [null] hello world Bob  [0]
....
```

We can launch the task with different parameters again:
```
dataflow:>task launch hello-world --arguments "--helloworld.greeting=Bill"
```

```
$ cf logs hello-world
...
2017-01-05T15:41:40.85+0100 [APP/TASK/hello-world4/0]OUT [null] hello world Bill  [0]
...
```
