FROM azul/zulu-openjdk-alpine:11

COPY ./target/scala-3.2.1/rallyeye-data.jar /app/app.jar
WORKDIR /app

EXPOSE 8080

ENTRYPOINT [ "java", "-jar", "app.jar" ]
