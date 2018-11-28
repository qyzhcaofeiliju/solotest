#!/bin/bash

OLD_PWD=$PWD
cd /usr/local/tomcat/webapps/ROOT

if [ "$ENV" == "dev" ]; then
    touch dev
elif [ "$ENV" == "test" ]; then
    touch test
elif [ "$ENV" == "prod" ]; then
    touch prod
fi

cd $OLD_PWD
exec "$@"
