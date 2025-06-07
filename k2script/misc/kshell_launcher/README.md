# KShell launcher for kscriptlets


Based on https://github.com/khud/sparklin which is a proof-of-concept software that includes experimental new Kotlin REPL called `KShell`.

## Installation

Since not all dependencies of `sparklin` are hosted on maven-central yet, we need to install some of its dependencies manually into our local maven repo

```bash
git clone https://github.com/khud/sparklin
cd sparklin
git checkout f200d1
mvn clean install

cd ..
git clone https://github.com/khud/kshell-repl-api
cd kshell-repl-api
git checkout c32e4e
mvn install
```

Now since all dependencies are met we can simply fetch the launcher script
```bash
cd ~/bin
wget https://raw.githubusercontent.com/holgerbrandl/kscript/master/misc/kshell_launcher/kshell_kts.sh
chmod +x kshell_kts.sh
```

You may want to add it to your `PATH` as well.

## Usage

Simply provide any kscript as argument. E.g [`krangl_example.kts`](https://github.com/holgerbrandl/kscript/blob/master/misc/kshell_launcher/krangl_example.kts)

```bash
kshell_kts.sh krangl_example.kts
```
This will launch a `kshell`-session with all dependencies from the kscriptlet in the class path

```
Preparing interactive session by resolving script dependencies...
[2] import krangl.schema
[3] import krangl.irisData
[4] irisData.schema()
DataFrame with 150 observations
Sepal.Length  [Dbl]  5.1, 4.9, 4.7, 4.6, 5, 5.4, 4.6, 5, 4.4, 4.9, 5.4, 4.8, 4.8, 4.3, 5.8, 5.7, 5.4,...
Sepal.Width   [Dbl]  3.5, 3, 3.2, 3.1, 3.6, 3.9, 3.4, 3.4, 2.9, 3.1, 3.7, 3.4, 3, 3, 4, 4.4, 3.9, 3.5...
Petal.Length  [Dbl]  1.4, 1.4, 1.3, 1.5, 1.4, 1.7, 1.4, 1.5, 1.4, 1.5, 1.5, 1.6, 1.4, 1.1, 1.2, 1.5, ...
Petal.Width   [Dbl]  0.2, 0.2, 0.2, 0.2, 0.2, 0.4, 0.3, 0.2, 0.2, 0.1, 0.2, 0.2, 0.1, 0.1, 0.2, 0.4, ...
Species       [Str]  setosa, setosa, setosa, setosa, setosa, setosa, setosa, setosa, setosa, setosa, ...
[6] 
```

## Todo

* bundle this with kscript itself