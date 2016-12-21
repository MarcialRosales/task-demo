package com.tasksample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.cloud.task.configuration.SimpleTaskConfiguration;
import org.springframework.cloud.task.configuration.TaskProperties;
import org.springframework.cloud.task.listener.TaskExecutionListener;
import org.springframework.cloud.task.repository.TaskExecution;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@SpringBootApplication
@EnableTask
public class TaskSampleApplication implements TaskExecutionListener {
	
	public static void main(String[] args) {
		SpringApplication.run(TaskSampleApplication.class, args);
		
		
	}

	@Override
	public void onTaskStartup(TaskExecution taskExecution) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTaskEnd(TaskExecution taskExecution) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onTaskFailed(TaskExecution taskExecution, Throwable throwable) {
		// TODO Auto-generated method stub
		System.out.println("task failed");
	}
	
	
}

@Component
class HelloWorld implements CommandLineRunner {

	@Autowired
	HelloWorldProperties config;
	
	@Autowired
	TaskProperties task;
	
	@Autowired
	SimpleTaskConfiguration cfg;
	
	@Override
	public void run(String... args) throws Exception {
		System.out.printf("[%s] hello world %s  [%d]\n", String.valueOf(task.getExecutionid()), config.getGreeting(), config.getTaskLengthSec());
		Thread.sleep(config.getTaskLengthSec() * 1000);
		if (config.getExitStatus() > 0) 
			throw new RuntimeException("exit status " + config.getExitStatus());
	}
	
	

	
}

@Configuration
@ConfigurationProperties(prefix = "helloworld")
class HelloWorldProperties {
	
	String greeting = "";
	long taskLengthSec;
	int exitStatus;
	
	
	public String getGreeting() {
		return greeting;
	}
	public void setGreeting(String greeting) {
		this.greeting = greeting;
	}
	public long getTaskLengthSec() {
		return taskLengthSec;
	}
	public void setTaskLengthSec(long taskLengthSec) {
		this.taskLengthSec = taskLengthSec;
	}
	public int getExitStatus() {
		return exitStatus;
	}
	public void setExitStatus(int exitStatus) {
		this.exitStatus = exitStatus;
	}
	
}