FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM gcr.io/distroless/java  
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/domegle-1.0.0-jar-with-dependencies.jar /usr/app/domegle-1.0.0-jar-with-dependencies.jar
ENTRYPOINT ["java","-jar","/usr/app/domegle-1.0.0-jar-with-dependencies.jar"]
