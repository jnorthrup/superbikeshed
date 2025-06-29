#!/usr/bin/env bash



########################################################################################################################
## Build and upload the image to the registry

cd ${KSCRIPT_HOME}/misc
#docker run --name kscript_interactive -it ubuntu:18.04

#docker build --no-cache -t kscript_tester .
docker build -t kscript_tester .

# quick test -> does it work (see section below)

#docker login

## create the versioned tag
docker tag kscript_tester holgerbrandl/kscript_tester:jdk11_kotlin1.3.31_gradle4.10
docker push holgerbrandl/kscript_tester:jdk11_kotlin1.3.31_gradle4.10

## create the latest tag
docker tag kscript_tester holgerbrandl/kscript_tester
docker push holgerbrandl/kscript_tester



########################################################################################################################
## Use the image for testing and debugging

cd ${KSCRIPT_HOME}


docker run -it --rm -v $(pwd)/kscript_docker:/kscript kscript_tester
docker run -it --rm -v $(pwd)/kscript_docker:/kscript a9945a6a860d

## with slash escaped for windows
#docker run -it --rm -v d:/projects/misc/kscript://kscript kscript_tester
#docker run -it -v ${pwd}:/kscript kscript_tester

## path path tp allow env usage within container
export PATH=/kscript/build/libs:$PATH
export KSCRIPT_HOME=/kscript


# https://stackoverflow.com/questions/28302178/how-can-i-add-a-volume-to-an-existing-docker-container
#docker commit a9945a6a860d kscript_tmp
#docker rm  `docker ps -q -l` # restart it in the background
#docker start  `docker ps -q -l` # restart it in the background
#docker attach `docker ps -q -l` # reattach the terminal & stdin

## rebuild
cd $KSCRIPT_HOME
./gradlew assemble

kscript --clear-cache

## run test suite
# git clone https://github.com/holgerbrandl/kscript kscript_docker
#cd kscript_docker
#test/test_suite.sh
${KSCRIPT_HOME}/test/test_suite.sh


## manual test dependency lookup
./kscript --clear-cache
rm -rf ~/.m2/; kscript --clear-cache
resolve_deps() { kotlin -classpath kscript.jar kscript.app.DependencyUtil "$@";}

resolve_deps log4j:log4j:1.2.14

mvn -f /tmp/__resdeps__temp__6026124609748923742_pom.xml dependency:build-classpath

## copy newer jar into container
#scp brandl@scicomp-mac-12-usb:/Users/brandl/projects/kotlin/kscript/kscript.jar .


##https://github.com/moby/moby/issues/26872
##https://stackoverflow.com/questions/38532483/where-is-var-lib-docker-on-mac-os-x


## network address docker ps && docker inspect <id> -> IPAdress
#https://stackoverflow.com/questions/17157721/how-to-get-a-docker-containers-ip-address-from-the-host
#sshfs -o root@172.17.0.3:/ /Users/brandl/Desktop/docker_container/ #-o idmap=user -o uid=1001 -o gid=1001
#sshfs -o 172.17.0.3:/ /Users/brandl/Desktop/docker_container/ #-o idmap=user -o uid=1001 -o gid=1001
