# This is a cut-down version of the file https://github.com/camunda/docker-camunda-bpm-platform/blob/next/Dockerfile
# We want to be as consistent with the Camunda distribution as makes sense
# without bringing in a lot of stuff that simply doesn't make sense
FROM alpine:3.18

ENV TZ=UTC
# Include any 'core' JDK options that we want to set (can still be overriden)
ENV JDK_JAVA_OPTIONS=""

# Add some useful packages to alpine base
RUN apk add --no-cache \
        bash \
        curl \
        openjdk17-jre-headless

# TODO Add files from the build...
