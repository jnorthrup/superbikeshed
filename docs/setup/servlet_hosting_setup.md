# Servlet Hosting with Tomcat on Docker Guide

This document provides instructions to compile, package, and deploy a simple Java servlet using Apache Tomcat running in a Docker container.

## 1. Prerequisites

*   **Java Development Kit (JDK):** Version 8 or later installed (for compiling the servlet). You can check with `java -version`.
*   **Apache Maven (or Gradle):** Installed for building the `.war` (Web Application Archive) file. This guide uses Maven. You can check with `mvn -version`.
*   **Docker:** Installed on your system.

## 2. Sample Servlet Code (`HelloWorldServlet.java`)

A basic servlet, `HelloWorldServlet.java`, is assumed to be provided (as created in a previous step). Its content should be similar to:

```java
package com.example;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class HelloWorldServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        PrintWriter out = resp.getWriter();
        out.println("<html>");
        out.println("<head><title>Hello Servlet</title></head>");
        out.println("<body>");
        out.println("<h1>Hello from Servlet!</h1>");
        out.println("</body>");
        out.println("</html>");
        out.close();
    }
}
```

**Project Structure:**
Place this file in a standard Java project directory structure:
`your_project_name/src/main/java/com/example/HelloWorldServlet.java`

## 3. Creating `web.xml` (Deployment Descriptor)

The `web.xml` file describes how your servlet is deployed and mapped to a URL.

Create the file `your_project_name/src/main/webapp/WEB-INF/web.xml` with the following content:

```xml
<!DOCTYPE web-app PUBLIC
 "-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN"
 "http://java.sun.com/dtd/web-app_2_3.dtd" >

<web-app>
  <display-name>HelloWorld Servlet Application</display-name>

  <servlet>
    <servlet-name>HelloWorldServlet</servlet-name>
    <servlet-class>com.example.HelloWorldServlet</servlet-class>
  </servlet>

  <servlet-mapping>
    <servlet-name>HelloWorldServlet</servlet-name>
    <url-pattern>/helloservlet</url-pattern>
  </servlet-mapping>

</web-app>
```
This maps the `HelloWorldServlet` to the URL pattern `/helloservlet`.

## 4. Building the `.war` file (Using Maven)

Create a `pom.xml` file in the root of `your_project_name/` with the following content:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>hello-servlet-app</artifactId>
  <packaging>war</packaging>
  <version>1.0-SNAPSHOT</version>
  <name>HelloWorld Servlet Webapp</name>
  <url>http://maven.apache.org</url>

  <properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>javax.servlet</groupId>
      <artifactId>javax.servlet-api</artifactId>
      <version>4.0.1</version> <!-- Or a version compatible with your Tomcat image -->
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>3.8.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>hello</finalName> <!-- This will be the name of the .war file (hello.war) -->
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-war-plugin</artifactId>
          <version>3.3.2</version>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.8.1</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

**Build Command:**
Navigate to the root of `your_project_name/` in your terminal and run:
```bash
mvn clean package
```
This command will compile your servlet and package it into `target/hello.war`.

## 5. Setting up Tomcat with Docker

*   **Image:** We'll use the official `tomcat:latest` Docker image (which typically includes a recent Tomcat version, e.g., Tomcat 9 or 10).
*   **Deploying the .war:** Copy the `hello.war` file (produced by Maven in the `target/` directory) into a local directory that you will mount into the Tomcat container. For example, create a directory `./my_war_files` in your project root and copy `target/hello.war` into it.
    ```bash
    mkdir my_war_files
    cp target/hello.war my_war_files/
    ```
*   **Port Mapping:** Tomcat's default HTTP port is `8080`. We'll map this to host port `8083` to avoid potential conflicts.

**Docker Run Command Example:**
Ensure `my_war_files/hello.war` exists relative to where you run this command.

```bash
# Get the absolute path to the current directory for volume mounting
ABS_PWD=$(pwd)

docker run -d \
    --name my_tomcat_server \
    -p 8083:8080 \
    -v "${ABS_PWD}/my_war_files:/usr/local/tomcat/webapps/" \
    tomcat:latest
```
**Command Breakdown:**
*   `-d`: Runs in detached mode.
*   `--name my_tomcat_server`: Assigns a name to the container.
*   `-p 8083:8080`: Maps host port `8083` to container port `8080`.
*   `-v "${ABS_PWD}/my_war_files:/usr/local/tomcat/webapps/"`: Mounts the local `my_war_files` directory (containing `hello.war`) into Tomcat's `webapps` directory. Tomcat will automatically deploy `.war` files placed here.
*   `tomcat:latest`: Specifies the Docker image.

Allow a few moments for Tomcat to start and deploy the application.

## 6. Verification

Once Tomcat is running and the `hello.war` is deployed, you can access your servlet at:
`http://localhost:8083/hello/helloservlet`

(The path is `/hello` because the `.war` file is named `hello.war`, and `/helloservlet` is the URL pattern defined in `web.xml`.)

You can use `curl` to test:
```bash
curl http://localhost:8083/hello/helloservlet
```
This should return the HTML: `<html><head><title>Hello Servlet</title></head><body><h1>Hello from Servlet!</h1></body></html>`

Check container logs if you encounter issues: `docker logs my_tomcat_server`

## 7. Stopping the Container

To stop your Tomcat container:
```bash
docker stop my_tomcat_server
```
To remove the container:
```bash
docker rm my_tomcat_server
```
