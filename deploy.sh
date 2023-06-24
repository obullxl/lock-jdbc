#!/bin/bash

# 仓库准备：提前准备好
#cd ../
#rm -rf maven-repository
#git clone -b master https://github.com/obullxl/maven-repository.git maven-repository

# 本地打包
#cd ./lock-jdbc
mvn clean && mvn deploy -Dmaven.test.skip=true

# 上传仓库
cd ./../maven-repository
git add --all
git commit -m 'Deploy lock-jdbc JAR: https://github.com/obullxl/lock-jdbc'
git push origin master

# 返回项目
cd ../lock-jdbc

# Gitee刷新：人工刷新
open -a '/Applications/Microsoft Edge.app' https://gitee.com/obullxl/maven-repository
