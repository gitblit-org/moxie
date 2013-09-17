#!/bin/sh

#
# Run some tests based on moxie proxy and gradle.
# Make sure that:
# - you're able to run moxie proxie, i.e. the required tcp ports aren't in use
# - you don't run jetty at the moment - it will be killed by this script :(
# - you have gradle available and you can run it via "gradle" (I'm using 1.8.-rc-1 at the moment)
#
# I've tested this script on Linux only.
#

D="$(dirname "$0")"
D="$(cd "${D}"; pwd)"

BN="$(basename "$0")"

GRADLE_CACHE="${D}/gradle-user-home"
MOXIE_PROXY="${D}/../../.."
MOXIE="${MOXIE_PROXY}/.."
MOXIE_PROXY_CACHE="${MOXIE_PROXY}/moxie"
MOXIE_PROXY_OUTPUT="${D}/moxie-proxie.output.txt"

# clean the moxie proxy cache
rm -rf "${MOXIE_PROXY_CACHE}"

firstColumn() {
  while read a b; do echo $a; done
}

killJetty () {
    ps -ao pid,command|grep jetty|grep -v grep|firstColumn|xargs -r kill -9
}

cleanGradleCache () {
  rm -rf "${GRADLE_CACHE}"
  find "${D}" -name .gradle|xargs -r rm -rf
}

cleanGradleBuilds () {
  find "${D}" -name build -o -name "*.log"|xargs -r rm -rf
}

cleanAllBuilds () {
  find "${MOXIE}" -name build -o -name "*.log"|xargs -r rm -rf
}

cleanMoxieProxyBuild () {
  find "${MOXIE_PROXY}" -name build -o -name "*.log"|xargs -r rm -rf
}

killJetty
cleanMoxieProxyBuild

# compile the moxie proxy
(
cd "${MOXIE_PROXY}"
ant
)

# run the moxie proxy
(
cd "${MOXIE_PROXY}"
ant run
) >"${MOXIE_PROXY_OUTPUT}" 2>&1  &

# clean the gradle cache
cleanGradleCache

RC=0
for d in "${D}"/*; do
  if [ -d "${d}" ]; then
    (
      cd "${d}"
      gradle --gradle-user-home "${GRADLE_CACHE}" test
    )
    _RC=$?
    if [ ${RC} -eq 0 ]; then
	RC="${_RC}"
    fi
  fi
done

rm -f "${MOXIE_PROXY_OUTPUT}"
killJetty
cleanGradleCache
cleanGradleBuilds

if [ ${RC} -ne 0 ]; then
  echo "${BN}: FAILURE, rc=${RC}"
fi
exit "${RC}"
