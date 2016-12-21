# Spring Cloud Task Demonstration Project

This project demonstrates how to execute tasks, a.k.a. one-off jobs, in a cloud platform like *Pivotal Cloud Foundry*. Unlike applications, a task typically executes for a limited amount of time and it does not expose any ports.

There are 2 ways to execute tasks: Directly in the platform or via Spring Cloud Data Flow.

1. [Launch Task natively in PCF](#launch-task-natively-in-pcf)
2. [Launch task via Spring Cloud Data Flow](#launch-task-via-spring-cloud-data-flow)

## Launch task natively in PCF

*Cloud Foundry* allows us to execute `Tasks` which are one-off jobs that are intended to perform a task, stop, and be cleaned up, freeing up resources.

Our task is a simple command-line Spring Boot application (`task-sample`) which we can launch it locally.
```
mvn install
java -jar target/task-sample-0.0.1-SNAPSHOT.jar --helloworld.greeting=Joe
```
And it produces a log statement like this one:
```
[null] hello world Joe  [0]
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
$ APP_GUID=$(cf app task-sample --guid)
$ TASK_COMMAND=$(cf curl /v3/apps/$APP_GUID/droplets/current | jq .result.process_types.web)
$ echo '{"command": '$TASK_COMMAND' }' > cmd.json
$ cf curl /v3/apps/$APP_GUID/tasks -X POST -d @cmd.json
```
Produces:
```
{
   "guid": "87684f9c-62e8-4584-8a23-246b24850a67",
   "sequence_id": 52,
   "name": "8dbecf28",
   "command": "CALCULATED_MEMORY=$($PWD/.java-buildpack/open_jdk_jre/bin/java-buildpack-memory-calculator-2.0.2_RELEASE -memorySizes=metaspace:64m..,stack:228k.. -memoryWeights=heap:65,metaspace:10,native:15,stack:10 -memoryInitials=heap:100%,metaspace:100% -stackThreads=300 -totMemory=$MEMORY_LIMIT) && JAVA_OPTS=\"-Djava.io.tmpdir=$TMPDIR -XX:OnOutOfMemoryError=$PWD/.java-buildpack/open_jdk_jre/bin/killjava.sh $CALCULATED_MEMORY\" && SERVER_PORT=$PORT eval exec $PWD/.java-buildpack/open_jdk_jre/bin/java $JAVA_OPTS -cp $PWD/. org.springframework.boot.loader.JarLauncher",
   "state": "RUNNING",
   "memory_in_mb": 1024,
   "disk_in_mb": 1024,
   "result": {
      "failure_reason": null
   },
   "created_at": "2016-12-21T14:19:19Z",
   "updated_at": "2016-12-21T14:19:20Z",
   "droplet_guid": "db82d05e-06f0-45a8-8971-7eeea488aeea",
   "links": {
      "self": {
         "href": "https://api.run.pez.pivotal.io/v3/tasks/87684f9c-62e8-4584-8a23-246b24850a67"
      },
      "app": {
         "href": "https://api.run.pez.pivotal.io/v3/apps/e4917000-fbde-45e7-8e9a-313af9ac9ef0"
      },
      "droplet": {
         "href": "https://api.run.pez.pivotal.io/v3/droplets/db82d05e-06f0-45a8-8971-7eeea488aeea"
      }
   }
}
```

We can check out the status of our task:
```
cf curl /v3/tasks/87684f9c-62e8-4584-8a23-246b24850a67
```
Produces:
```
{
   "guid": "87684f9c-62e8-4584-8a23-246b24850a67",
   "sequence_id": 52,
   "name": "8dbecf28",
   "command": "CALCULATED_MEMORY=$($PWD/.java-buildpack/open_jdk_jre/bin/java-buildpack-memory-calculator-2.0.2_RELEASE -memorySizes=metaspace:64m..,stack:228k.. -memoryWeights=heap:65,metaspace:10,native:15,stack:10 -memoryInitials=heap:100%,metaspace:100% -stackThreads=300 -totMemory=$MEMORY_LIMIT) && JAVA_OPTS=\"-Djava.io.tmpdir=$TMPDIR -XX:OnOutOfMemoryError=$PWD/.java-buildpack/open_jdk_jre/bin/killjava.sh $CALCULATED_MEMORY\" && SERVER_PORT=$PORT eval exec $PWD/.java-buildpack/open_jdk_jre/bin/java $JAVA_OPTS -cp $PWD/. org.springframework.boot.loader.JarLauncher",
   "state": "SUCCEEDED",
   "memory_in_mb": 1024,
   "disk_in_mb": 1024,
   "result": {
      "failure_reason": null
   },
   "created_at": "2016-12-21T14:19:19Z",
   "updated_at": "2016-12-21T14:19:30Z",
   "droplet_guid": "db82d05e-06f0-45a8-8971-7eeea488aeea",
   "links": {
      "self": {
         "href": "https://api.run.pez.pivotal.io/v3/tasks/87684f9c-62e8-4584-8a23-246b24850a67"
      },
      "app": {
         "href": "https://api.run.pez.pivotal.io/v3/apps/e4917000-fbde-45e7-8e9a-313af9ac9ef0"
      },
      "droplet": {
         "href": "https://api.run.pez.pivotal.io/v3/droplets/db82d05e-06f0-45a8-8971-7eeea488aeea"
      }
   }
}
```

## Launch task via Spring Cloud Data Flow

### Create our simple `hello world` task

We create a simple Spring Boot app called `task-sample` and we execute it locally by running this simple statements:

Launch it with a given greeting message and the task takes at least 3 seconds to execute
```
mvn install
java -jar target/task-sample-0.0.1-SNAPSHOT.jar --helloworld.greeting=Bob --helloworld.taskLengthSec=3
```

Launch the task so that it fails
```
java -jar target/task-sample-0.0.1-SNAPSHOT.jar --helloworld.greeting=Bob --helloworld.taskLengthSec=3 --helloworld.exitStatus=1
```

Let's launch this task thru the Spring Cloud Data Flow server. This is a server that allows us to launch our tasks. Why do we need another server to run our tasks? Because we want to run our task in other environments, not only in our local machine. For instance, we can run our task in Cloud Foundry, or Apache YARN or locally.

About our `task-sample` application:
- It is a standard Spring Boot app with a new annotation, `@EnableTask`. This annotation makes this application `suitable` to run within Spring Cloud Data Flow server as a **Task**.
- In addition to the `@EnableTask` annotation, we've added a `CommandLineRunner` bean that contains the logic for our task.
- It has a new dependency
```
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-task-starter</artifactId>
  <version>1.0.2.RELEASE</version>
</dependency>
```

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
