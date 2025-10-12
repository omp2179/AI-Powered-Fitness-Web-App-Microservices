#!/bin/sh

git filter-branch --env-filter '
# This is the old email from your global config
OLD_EMAIL="139671544+4heisenberG4@users.noreply.github.com"

# These are your correct main account details
CORRECT_NAME="omp2179"
CORRECT_EMAIL="202301163@dau.ac.in"

if [ "$GIT_COMMITTER_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_COMMITTER_NAME="$CORRECT_NAME"
    export GIT_COMMITTER_EMAIL="$CORRECT_EMAIL"
fi
if [ "$GIT_AUTHOR_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_AUTHOR_NAME="$CORRECT_NAME"
    export GIT_AUTHOR_EMAIL="$CORRECT_EMAIL"
fi
' --tag-name-filter cat -- --branches --tags