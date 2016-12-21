#!/bin/bash

set -e

echo "Checking task $1"

TASK=`cf curl /v3/tasks/$1`

TASK_STATE=`echo $TASK | jq .state`

echo "$TASK_STATE "
