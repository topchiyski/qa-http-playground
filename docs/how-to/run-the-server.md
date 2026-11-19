# Run the server

Run these commands from the repository root. On Windows, type `.\mvnw` in PowerShell or `mvnw` in `cmd.exe`.

## Start and stop it

```bash
./mvnw spring-boot:run
```

Wait for `Started PlaygroundApplication`, then open <http://localhost:8080/> for the endpoint list or
<http://localhost:8080/swagger-ui> to try each endpoint in the browser. Stop it with `Ctrl+C`.

Server metrics in Prometheus format are at <http://localhost:8081/actuator/prometheus>. To change a port or a limit,
see [Configuration](../configuration.md).

## Run it as a standalone jar

Build the jar once, then run it on any machine with Java 21:

```bash
./mvnw package -DskipTests
java -jar playground-server/target/playground-server-0.1.0-SNAPSHOT-exec.jar
```

## Run it with fixed resources

Start the jar with a fixed CPU count and heap size when you load-test it:

```bash
java -XX:ActiveProcessorCount=2 -Xmx1g -jar playground-server/target/playground-server-0.1.0-SNAPSHOT-exec.jar
```

The JVM then sizes its thread pools and garbage collector for 2 CPUs and uses at most 1 GB of heap. Results become
easier to compare between runs and machines.

This is not a hard CPU limit: the operating system can still use every core. Report it as "the playground configured
for 2 CPUs".

## Run it on another machine

Put the playground and the load generator on separate machines on the same wired network. Only then do you measure
the server instead of the load generator. Start the jar on the server machine, then point Gatling at it:

```bash
./mvnw -pl examples-gatling gatling:test -DbaseUrl=http://<server>:8080
```

Replace `<server>` with the server machine's name or IP address.
