#!/bin/bash

function gradle {
  ./gradlew "$@"
}

export -f gradle
