#!/bin/bash
pid=`jps -lvm|grep SurveySystem|awk '{print $1}'`
if [ "$pid" == "" ]
then
        echo "进程已经关闭"
else
        kill -9 $pid
        echo "KILL $pid"
fi