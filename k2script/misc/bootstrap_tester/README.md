# Test Protocol for Bootstrap Header

```bash
cd $KSCRIPT_HOME/misc/bootstrap_tester
docker build -t bstester .

echo "println(42)" >> bs_test.kts

# Enrich the script with a bootstrap header
export PATH=${KSCRIPT_HOME}/build/libs:${PATH}
which kscript
#kscript --help

kscript  --add-bootstrap-header bs_test.kts
cat bs_test.kts

# spin up the container to test the 
#docker run --rm -v ${PWD}:/apps -it ubuntu 
docker run --rm -v ${PWD}:/apps -it bstester 
```

Within the container run
```bash
cd /apps
bash bs_test.kts
```
