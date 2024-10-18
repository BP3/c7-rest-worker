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

ENTRYPOINT ["./docker-entrypoint.sh"]
WORKDIR /worker

RUN addgroup -g 1000 -S bp3global && \
    adduser -u 1000 -S bp3user -G bp3global -h /worker -s /bin/bash -D bp3global
USER bp3user

# Add files from the build
ADD --chown=bp3user:bp3global . /worker
