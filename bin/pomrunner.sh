#!/bin/sh
JAVA=java
CPF=mvn.classpath
if [ ! -e $CPF ] || [ $CPF -ot pom.xml ]  ; then
	touch $CPF
	mvn compile dependency:build-classpath -Dmdep.outputFile=$CPF
	if [ $? -eq 0 ] ; then
		echo "building classpath complete"
	else
		echo "ERROR cannot build classpath"
		exit -1
	fi
fi
BCP=`cat $CPF`
CP=target/test-classes:target/classes:$BCP
$JAVA -cp $CP "$@"
