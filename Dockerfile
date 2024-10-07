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

# We want to ensure that we bust the docker build cache whenever the
# contents of the repo change, i.e. there are new commits (to main).
# Without busting the cache at this point then we would need to use
# the --no-cache argument to docker build to ensure that it is refreshed
# each time it is built - thereby losing the benefit of having the above
# layers already in the cache.
ADD https://github.com/BP3/c7-rest-worker/commits /dev/null

# Add files from the build
ADD . /worker
