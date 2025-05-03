FROM maven:3.8.3-openjdk-17 as build
WORKDIR /workspace/app
 # Копируем pom.xml и исходный код в контейнер
COPY pom.xml .
COPY src src

# Сборка проекта с пропуском тестов
RUN mvn -DskipTests=true clean package

# Распаковка содержимого jar-файла
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:17-jre-alpine

# Указание пути для зависимостей
ARG DEPENDENCY=/workspace/app/target/dependency

# Копируем зависимости и классы приложения
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF

# Установка точки входа
ENTRYPOINT ["java","-cp","app:app/lib/*","com.warehouse.WarehouseQrApplication"]