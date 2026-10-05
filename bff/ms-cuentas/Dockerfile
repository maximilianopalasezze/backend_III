FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY target/*-SNAPSHOT.jar app.jar

EXPOSE 8091

ENTRYPOINT ["java","-Xms64m","-Xmx192m","-jar","/app/app.jar"]
