# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-nanum && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*SNAPSHOT.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx400m -Duser.timezone=Asia/Seoul"
EXPOSE 5555
CMD ["java","-jar","app.jar"]
