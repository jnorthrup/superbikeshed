# start container with
#docker pull maven:3.5.4-jdk-8
#docker run --rm --name sparklin -it maven:3.5.4-jdk-8 /bin/bash --login


## install kotlin, maven and gradle
apt-get update
apt-get install zip
curl -s 'https://get.sdkman.io' | bash
source ~/.sdkman/bin/sdkman-init.sh && sdkman_auto_answer=true && sdk install kotlin && sdk install maven && sdk install gradle && sdk install kscript

cd /
git clone https://github.com/khud/sparklin
cd sparklin
git checkout f200d1461d7f5f6ca625c39fb7915d4fa71eb56e
mvn clean install

cd /
git clone https://github.com/khud/kshell-repl-api
cd kshell-repl-api
git checkout c32e4e
mvn install

## test the orignal launcher
#../sparklin/bin/kshell.sh

cd /
wget https://raw.githubusercontent.com/holgerbrandl/kscript/master/misc/kshell_launcher/kshell_kts.sh
chmod +x kshell_kts.sh

echo  '@file:DependsOn("de.mpicbg.scicomp:krangl:0.9.1")' >> example.kts

./kshell_kts.sh example.kts
