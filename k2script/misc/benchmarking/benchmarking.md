
```bash

cd ${KSCRIPT_HOME}

## make sure to use devel-version
export PATH=${KSCRIPT_HOME}:${PATH}
which kscript

#https://stackoverflow.com/questions/9257533/what-is-the-difference-between-origin-and-upstream-on-github

prNumber=93
git fetch upstream pull/${prNumber}/head:pr${prNumber}
git checkout pr${prNumber}
./gradlew shadowJar

## see https://stackoverflow.com/questions/6245570/how-to-get-the-current-branch-name-in-git
misc/benchmarking/benchmark_kscript.kts scriptlet_runtimes_$(git rev-parse --abbrev-ref HEAD).txt


## roll back to master and redo the benchmarking
git checkout master
./gradlew clean assemble

misc/benchmarking/benchmark_kscript.kts scriptlet_runtimes_$(git rev-parse --abbrev-ref HEAD)-$(git rev-parse --short HEAD).txt

## render the report (see https://github.com/holgerbrandl/datautils/tree/master/tools/rendr)
rend.R misc/benchmarking/benchmark_kscript.R


## export commit history for date references
# https://stackoverflow.com/questions/14243380/how-to-configure-git-log-to-show-commit-date
#https://stackoverflow.com/questions/3631005/git-log-tabular-formatting

git log --pretty=format:'%h %<(20)%an %s %cd' > kscript_commit_history.txt

```

