#!/bin/bash

set -e

if [ ! -d "tmp" ]; then
  mkdir tmp
fi

APP_GUID=`cf app $1 --guid`

echo "Executing $1 with GUI $APP_GUID"

function cf_task() { echo {\"command\": $(cf curl /v3/apps/$1/droplets/current | jq .result.process_types.web) }; }

cf_task $APP_GUID > tmp/cmd.json

echo "Launching task ... "
TASK=`cf curl /v3/apps/$APP_GUID/tasks -X POST -d @tmp/cmd.json`

TASK_GUID=`echo $TASK | jq .guid`
TASK_STATE=`echo $TASK | jq .state`

echo "$1 $TASK_STATE ($TASK_GUID)"
