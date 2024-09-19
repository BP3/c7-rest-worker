
docker run -it --rm \
--mount type=bind,src=$(PWD),dst=/builds/camunda/c7-rest-worker \
--name gradle -w /builds/camunda/c7-rest-worker \
-e GRADLE_USER_HOME=/builds/camunda/c7-rest-worker/.gradle \
gradle:jdk17 gradle --info --build-cache assemble

