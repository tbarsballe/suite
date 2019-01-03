/*
Boundless Server build pipeline.
POC- Nick Stires

Build requirements:
* nvm
* npm
** 2.15.9 (default)
** 6.0.0 (available)
* openjdk
** 1.8.0_101
* ant
** 1.8.3
* mvn
** 3.3.9
* rpmbuild
* docker
* bouncy-castle (for OWASP dep-checker)
** (ref) https://stackoverflow.com/questions/31971499/ecdhe-cipher-suites-not-supported-on-openjdk-8-installed-on-ec2-linux-machine/33521718#33521718
** https://www.bouncycastle.org/download/bcprov-jdk15on-160.jar /usr/lib/jvm/jre-1.8.0-openjdk.x86_64/lib/ext

Source repos:
* https://github.com/boundlessgeo/suite
** (with submodules)

Artifacts out:
WORKSPACE/archive/
*/

pipeline {

  agent {
    label 'jenkins-slave-01.boundlessgeo.com'
  }
  
  options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '5'))
    }

  stages {
  	stage('Init') {
      steps {
        // Set variables for build types (master/stable)
        // Variables below called through legacy maven/ant build definitions
        setEnvs()

        // Clean workspace
        makeDir("$ARTIFACT_DIR")
        makeDir("$WORKSPACE/SRC")
        makeDir("$WORKSPACE/archive/el/6/")
        makeDir("$WORKSPACE/archive/el/7/")
        makeDir("$WORKSPACE/archive/ubuntu/14/")
        makeDir("$WORKSPACE/archive/ubuntu/16/")
        makeDir("$WORKSPACE/archive/war/")
        makeDir("$WORKSPACE/archive/zip/")
        makeDir("$WORKSPACE/archive/owasp")

        gitCheckoutRecursive('suite','$BRANCH_NAME')

        // Store short SHA of repo into variable
        // Used for artifact capture
        captureHead()

        antBuild('suite/build/versions.xml','set-versions')
      }
    }


    stage('Build-Composer') {
      steps {
        sonarScan('suite/composer/composer/app','Composer')
        antBuild('suite/composer/build.xml','clean build assemble publish')
        archiveBuildZip('composer')
      }
    }
    stage('Build-Dashboard') {
      steps {
        sonarScan('suite/dashboard/src/main/webapp','Dashboard')
        antBuild('suite/dashboard/build.xml','clean build assemble publish')
        archiveBuildZip('dashboard')
      }
    }
    stage('Build-WPSBuilder') {
      steps {
        sonarScan('suite/wpsbuilder/wps-gui/src','WPSBuilder')
        antBuild('suite/wpsbuilder/build.xml','clean build assemble publish')
        archiveBuildZip('wpsbuilder')
      }
    }
    stage('Build-GeoTools') {
      steps {
        sonarScan('suite/geoserver/geotools/','GeoTools',true, 
          // This file causes a stackoverflow when scanning; skip it
          "suite/geoserver/geotools/geotools/modules/unsupported/arcgis-rest/src/main/java/org/geotools/data/arcgisrest/schema/webservice/Attributes.java")
        antBuild('suite/geoserver/geotools/build.xml','clean build assemble publish')
      }
    }

    stage('Build-Quickview') {
      steps {
        script {
          packageRPMs("quickview")
          //sonarScan('suite/quickview','quickview')
          sh "suite/packaging/el/rpm-resign.exp archive/el/6/*.rpm || true"
        }
        archiveArtifacts artifacts: "archive/el/6/${BRANDING}-quickview-*.rpm", fingerprint: true
      }
    }

    stage('Build-GeoWebCache') {
      steps {
        sonarScan('suite/geoserver/geowebcache','GeoWebCache',true)
        antBuild('suite/geoserver/geowebcache/build.xml','clean build assemble publish')
      }
    }

    stage('Build-GeoServer') {
      steps {
        sonarScan('suite/geoserver/geoserver','GeoServer',true)
        antBuild('suite/geoserver/geoserver/build.xml','clean build assemble publish')
        script {
          geoServerExtensions = ['app-schema', 'arcsde', 'csw', 'db2', 'gdal', 'grib', 'inspire', 'jp2k', 'mongodb', 'netcdf', 'netcdf-out', 'oracle', 'sqlserver', 'vectortiles']
          for (int i = 0; i < geoServerExtensions.size(); i++) {
            archiveBuildZip("${geoServerExtensions[i]}")
          }
        }
      }
    }

    stage('Build-DataDir') {
      steps {
        sonarScan('suite/geoserver/data_dir','DataDir')
        antBuild('suite/geoserver/data_dir/build.xml','clean build assemble publish')
        archiveBuildZip('data-dir')
      }
    }
    stage('Build-Docs') {
      steps {
        sonarScan('suite/docs','Docs')
        antBuild('suite/docs/build.xml','clean build assemble publish')
        archiveBuildZip('docs-war')
      }
    }
    stage('Build-GeoMesa') {
      steps {
        sonarScan('suite/geoserver/externals/geomesa/','GeoMesa',true)
        antBuild('suite/geoserver/externals/geomesa/build.xml','clean build assemble publish')
        archiveBuildZip('geomesa-accumulo-distributed-runtime')
      }
    }
    stage('Build-GeoScript') {
      steps {
        sonarScan('suite/geoserver/externals/geoscript/','GeoScript',true)
        antBuild('suite/geoserver/externals/geoscript/build.xml','clean build assemble publish')
        archiveBuildZip('geoscript-py')
      }
    }
    stage('Build-Marlin') {
      steps {
        sonarScan('suite/geoserver/externals/marlin/','Marlin',true)
        antBuild('suite/geoserver/externals/marlin/build.xml','clean build assemble publish')
        archiveBuildZip('marlin')
      }
    }
    stage('Build-SpatialStatistics') {
      steps {
        sonarScan('suite/geoserver/externals/spatialstatistics/','SpatialStatistics',true)
        antBuild('suite/geoserver/externals/spatialstatistics/build.xml','clean build assemble publish')
      }
    }
    stage('Build-GSR') {
      steps {
        sonarScan('suite/geoserver/externals/gsr/','GSR',true)
        antBuild('suite/geoserver/externals/gsr/build.xml','clean build assemble publish')
        archiveBuildZip('gsr')
      }
    }
    // stage('Build-GeoGig') {
    //   steps {
    //     sonarScan('suite/geoserver/geoserver/geoserver/src/community/geogig/','GeoGig',true)
    //     script {
    //       sh """
    //         cd ${WORKSPACE}/suite/geoserver/geoserver/geoserver
    //         mvn -f src/community/geogig/pom.xml clean install -Dcucumber.options="--monochrome --plugin json:target/cucumber.json" -Dtest.maxHeapSize=1G
    //         mvn clean install -DskipTests -f src/community/pom.xml -P communityRelease assembly:attached
    //       """
    //       geoGigZip = sh (script: "find ${WORKSPACE}/suite/geoserver/geoserver/geoserver/src/community/target/release/ \
    //       -name *geogig-plugin.zip", returnStdout:true).trim()
    //       sh "cp -p $geoGigZip $WORKSPACE/archive/zip/${BRANDING}-geoserver-geogig-${SERVER_HEAD}.zip"
    //       archiveArtifacts artifacts: "archive/zip/${BRANDING}-geoserver-geogig-${SERVER_HEAD}.zip", fingerprint: true
    //     }
    //   }
    // }

    stage('Build-Community') {
      steps {
        antBuild('suite/geoserver/geoserver-community/build.xml','clean build assemble publish')
        script {
          geoServerExtensions = ['geopkg', 'jdbcconfig', 'jdbcstore']
          for (int i = 0; i < geoServerExtensions.size(); i++) {
            archiveBuildZip("${geoServerExtensions[i]}")
          }
        }
      }
    }

    // stage('Build-GSExts') {
    //   steps {
    //     sonarScan('suite/geoserver/externals/geoserver-exts/','GeoServer-Exts',true)
    //     antBuild('suite/geoserver/externals/geoserver-exts/build.xml','clean build assemble publish')
    //     archiveBuildZip('cloudwatch')
    //     archiveBuildZip('printng')
    //   }
    // }

    stage('Build-WebApp') {
      steps {
        sonarScan('suite/geoserver/webapp/','WebApp',true)
        antBuild('suite/geoserver/webapp/build.xml','clean build assemble publish')
        archiveBuildZip('geoserver')
      }
    }

    stage('Build-GWCWar') {
      steps {
        antBuild('suite/geowebcache/build.xml','clean build assemble publish')
        archiveBuildZip('geowebcache')
      }
    }

    stage('Archive-WARs') {
      steps {
        packageWars()
        archiveArtifacts artifacts: "archive/war/${ARCHIVE_BASENAME}-*.zip", fingerprint: true
      }
    }
    
    stage('OWASP-DepCheck') {
      steps {
        makeDir("$WORKSPACE/tmp/zip/")
        makeDir("$WORKSPACE/tmp/jar/")
        withCredentials([string(credentialsId: 'sonarQubeToken', variable: 'SONAR_QUBE_TOKEN')]) {
          sh """
            unzip archive/war/BoundlessServer-*ext.zip -d tmp/zip/
            unzip archive/war/BoundlessServer-*war.zip -d tmp/zip/
            find archive/war/ -type l -delete
            cd tmp/zip/BoundlessServer-*-war
            
            for i in `ls`; do
              j=`echo \$i | awk -F. '{print \$1}'`
              unzip \$i -d \$j
            done
            
            cd $WORKSPACE
            for i in `find tmp/zip/ -name *.jar`; do
              mv \$i tmp/jar/
            done
            
            cd archive/owasp/
            owasp-dependency-check \
              --project "[Server] OWASP Dependency Checker" \
              --scan "$WORKSPACE/tmp/jar/" \
              --format ALL
            cd $WORKSPACE
            
            sonar-scanner \
              -Dsonar.sources=$WORKSPACE/archive/owasp/ \
              -Dsonar.projectName="[Server] OWASP Dependency Checker" \
              -Dsonar.projectKey=org.boundlessgeo:owasp-all \
              -Dsonar.host.url=${SONAR_HOST_URL} \
              -Dsonar.login=${SONAR_QUBE_TOKEN} \
              -Dsonar.dependencyCheck.reportPath=$WORKSPACE/archive/owasp/dependency-check-report.xml \
              -Dsonar.dependencyCheck.htmlReportPath=$WORKSPACE/archive/owasp/dependency-check-report.html
          """
        }
      }
    }

    stage('Build-RPMs') {
      steps {
        explodeSources()
        // Build RPMs then cleanup
        script {
          serverRPMs = ['composer', 'dashboard', 'docs', 'geoserver', 'geowebcache', 'gs-appschema', 'gs-arcsde', 'gs-csw', 'gs-db2', 'gs-gdal', 'gs-geomesa-accumulo', 'gs-grib', 'gs-gsr', 'gs-inspire', 'gs-jdbcconfig', 'gs-jdbcstore', 'gs-jp2k', 'gs-mongodb', 'gs-netcdf', 'gs-netcdf-out', 'gs-oracle', 'gs-sqlserver', 'gs-vectortiles', 'wpsbuilder']
          for (int i = 0; i < serverRPMs.size(); i++) {
            packageRPMs("${serverRPMs[i]}")
          }
          sh "rm -rf ${WORKSPACE}/${BRANDING}-*"
        }
        script {
          // Sign RPMs
          sh "suite/packaging/el/rpm-resign.exp archive/el/6/*.rpm || true"
        }
        archiveArtifacts artifacts: "archive/el/6/*.rpm", fingerprint: true
      }
    }

    stage('Build-Debs') {
      steps {
        // Replace versions in dependencies
        script {
          sh """
            sed -i "s:CURRENT_VER:${PACKAGE_VERSION}:g" ${WORKSPACE}/suite/packaging/ubuntu/14/depends.esv
            sed -i "s:CURRENT_VER:${PACKAGE_VERSION}:g" ${WORKSPACE}/suite/packaging/ubuntu/16/depends.esv
            sed -i "s:NEXT_VER:${NEXT_VER}:g" ${WORKSPACE}/suite/packaging/ubuntu/14/depends.esv
            sed -i "s:NEXT_VER:${NEXT_VER}:g" ${WORKSPACE}/suite/packaging/ubuntu/16/depends.esv
          """
        }
        // Issue with building Debs on EL6. Much of task to take place tentatively on priv-repo server, which runs Ubuntu 14
        // Perform workspace cleanup then send conversion scripts, sources, and supporting files
        script {
          sh """
            ssh root@priv-repo.boundlessgeo.com '
              rm -rf /tmp/convert-${BUILD_TYPE}
              mkdir -p /tmp/convert-${BUILD_TYPE}/rpms-in
              '
            scp ${WORKSPACE}/archive/el/6/*.rpm root@priv-repo.boundlessgeo.com:/tmp/convert-${BUILD_TYPE}/rpms-in/
          """
        }
        // Build Ubuntu 14 Debs
        script {
          serverUbuntu14Debs = ['composer', 'dashboard', 'docs', 'geoserver', 'geowebcache', 'gs-appschema', 'gs-arcsde', 'gs-csw', 'gs-db2', 'gs-gdal', 'gs-geomesa-accumulo', 'gs-grib', 'gs-gsr', 'gs-inspire', 'gs-jdbcconfig', 'gs-jdbcstore', 'gs-jp2k', 'gs-mongodb', 'gs-netcdf', 'gs-netcdf-out', 'gs-oracle', 'gs-sqlserver', 'gs-vectortiles', 'wpsbuilder', 'quickview']
          for (int i = 0; i < serverUbuntu14Debs.size(); i++) {
            debConvert("${serverUbuntu14Debs[i]}",'14')
          }
        }

        // Build Ubuntu 16 Debs
        debConvert("gs-gdal",'16')

        // Pull built debs
        script {
          sh """
            scp root@priv-repo.boundlessgeo.com:/tmp/convert-${BUILD_TYPE}/debs-out/ubuntu/14/* archive/ubuntu/14/
            scp root@priv-repo.boundlessgeo.com:/tmp/convert-${BUILD_TYPE}/debs-out/ubuntu/16/* archive/ubuntu/16/
          """
        }
        // Reuse Ubuntu 14 Debs as able
        script {
          clonedDebs = ['composer', 'dashboard', 'docs', 'geoserver', 'geowebcache', 'gs-appschema', 'gs-arcsde', 'gs-csw', 'gs-db2', 'gs-geomesa-accumulo', 'gs-grib', 'gs-gsr', 'gs-inspire', 'gs-jdbcconfig', 'gs-jdbcstore', 'gs-jp2k', 'gs-mongodb', 'gs-netcdf', 'gs-netcdf-out', 'gs-oracle', 'gs-sqlserver', 'gs-vectortiles', 'wpsbuilder', 'quickview']
          for (int i = 0; i < clonedDebs.size(); i++) {
            sh "cp -p ${WORKSPACE}/archive/ubuntu/14/${BRANDING}-${clonedDebs[i]}_${PACKAGE_VERSION}-*.deb ${WORKSPACE}/archive/ubuntu/16/"
          }
        }
        archiveArtifacts artifacts: "archive/ubuntu/14/*.deb", fingerprint: true
        archiveArtifacts artifacts: "archive/ubuntu/16/*.deb", fingerprint: true
      }
    }

    stage('Update-Repo') {
      steps {
        // Remove old wars
        script {
          sh "ssh root@priv-repo.boundlessgeo.com rm -rf /var/www/repo/suite/${BUILD_TYPE}/war/*"
        }
        // Send artifacts
        // Note- no EL7 unique RPMs, reusing EL6
        script {
          sh """
            ssh root@priv-repo.boundlessgeo.com 'mkdir -p /var/www/repo/suite/${BUILD_TYPE}/war/'
            scp archive/war/${ARCHIVE_BASENAME}-*.zip root@priv-repo.boundlessgeo.com:/var/www/repo/suite/${BUILD_TYPE}/war/
            
            ssh root@priv-repo.boundlessgeo.com 'mkdir -p /var/www/repo/suite/${BUILD_TYPE}/el/6/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/el/6/*.rpm /var/www/repo/suite/${BUILD_TYPE}/el/6/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/el/6/*.xml /var/www/repo/suite/${BUILD_TYPE}/el/6/'
            scp archive/el/6/*.rpm root@priv-repo.boundlessgeo.com:/var/www/repo/suite/${BUILD_TYPE}/el/6/
            
            ssh root@priv-repo.boundlessgeo.com 'mkdir -p /var/www/repo/suite/${BUILD_TYPE}/el/7/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/el/7/*.rpm /var/www/repo/suite/${BUILD_TYPE}/el/7/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/el/7/*.xml /var/www/repo/suite/${BUILD_TYPE}/el/7/'
            scp archive/el/6/*.rpm root@priv-repo.boundlessgeo.com:/var/www/repo/suite/${BUILD_TYPE}/el/7/
            
            ssh root@priv-repo.boundlessgeo.com 'mkdir -p /var/www/repo/suite/${BUILD_TYPE}/ubuntu/14/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/ubuntu/14/*.deb /var/www/repo/suite/${BUILD_TYPE}/ubuntu/14/'
            scp archive/ubuntu/14/*.deb root@priv-repo.boundlessgeo.com:/var/www/repo/suite/${BUILD_TYPE}/ubuntu/14/
            
            ssh root@priv-repo.boundlessgeo.com 'mkdir -p /var/www/repo/suite/${BUILD_TYPE}/ubuntu/16/'
            ssh root@priv-repo.boundlessgeo.com '/bin/cp -f /var/www/repo/third-party/ubuntu/16/*.deb /var/www/repo/suite/${BUILD_TYPE}/ubuntu/16/'
            scp archive/ubuntu/16/*.deb root@priv-repo.boundlessgeo.com:/var/www/repo/suite/${BUILD_TYPE}/ubuntu/16/
          """
        }
        
        trimRepo()
        
        // Resign 3rd party RPMs
        script {
          sh """
            ssh root@priv-repo.boundlessgeo.com '
              /root/rpm-resign.exp /var/www/repo/suite/${BUILD_TYPE}/el/6/[!boundless-server-]*.rpm
              /root/rpm-resign.exp /var/www/repo/suite/${BUILD_TYPE}/el/6/boundless-server-tomcat*.rpm
              /root/rpm-resign.exp /var/www/repo/suite/${BUILD_TYPE}/el/7/[!boundless-server-]*.rpm
              /root/rpm-resign.exp /var/www/repo/suite/${BUILD_TYPE}/el/7/boundless-server-tomcat*.rpm
            '
          """
        }
        // Update repo metadata
        script {
          sh """
            ssh root@priv-repo.boundlessgeo.com '
              cd /var/www/repo/suite/${BUILD_TYPE}/el/6/
              createrepo -g server-groups.xml .
              cd /var/www/repo/suite/${BUILD_TYPE}/el/7/
              createrepo -g server-groups.xml .
              cd /var/www/repo/suite/${BUILD_TYPE}/ubuntu/14
              dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
              cd /var/www/repo/suite/${BUILD_TYPE}/ubuntu/16
              dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
            '
          """
        }
      }
    }

    stage('Build-Docker') {
      steps {
        withCredentials([string(credentialsId: 'PRIV_REPO_PASSWORD', variable: 'PRIV_REPO_PASSWORD')]) {
          script{
            sh """
              cd $WORKSPACE/suite/packaging/docker
              echo "Building version: ${DOCKER_VER}"
              sed -i "s/REPLACE_VERSION/${BUILD_TYPE}/g" Dockerfile
              sed -i "s/PRIV_REPO_PASSWORD/${PRIV_REPO_PASSWORD}/" Dockerfile
              docker build -t quay.io/boundlessgeo/server:$DOCKER_VER .
              docker push quay.io/boundlessgeo/server:$DOCKER_VER
            """
          }
        }
      }
    }

    stage('Update-Demo') {
      steps {
        script {
          if ( BUILD_TYPE.equals('master') ) {
              sh """
                ssh root@demo-master.boundlessgeo.com '
                  yum clean all
                  rm -rf /var/cache/yum
                  yum update -y boundless-server-*
                  service tomcat8 restart
                '
              """
          } else {
            echo "Demo update for release is a manual task, skipping..."
          }
        }
      }
    }

    stage('Slack-Notif') {
      steps {
        script {
          sh """
            cd suite/packaging/slack_notif
            sed -i 's/REPLACE_BRANCH/${BRANCH_NAME}/g' bots-message.txt
            cat bots-message.txt | bash slacktee.sh -n -p
            exit 0
          """
        }
      }
    }
  }
  
  post {
    always {
      cleanWs()
    }
  }
}


def makeDir(def dir) {
  dirCheck = sh (script: "test -d ${dir} && echo '1' || echo '0' ", returnStdout: true).trim()
  if (dirCheck=='1') {
    echo "Cleaning contents of following directory: $dir"
    sh "rm -rf $dir"
  }
  sh "mkdir -p $dir"
}

def gitCheckout(def repo, def branch) {
  sh """
    rm -rf $repo
    git clone "git@github.com:boundlessgeo/${repo}.git"
    cd $repo
    git checkout $branch
  """
}

def gitCheckoutRecursive(def repo, def branch) {
  sh """
    rm -rf $repo
    git clone "git@github.com:boundlessgeo/${repo}.git"
    cd $repo
    git checkout $branch
    git submodule foreach git reset --hard HEAD || true
    git submodule update --init --recursive
  """
}

def setEnvs() {
  env.BUILD_TYPE = "$BRANCH_NAME"
  env.BRANDING = 'boundless-server'
  env.ARCHIVE_BASENAME = 'BoundlessServer'
  env.LICENSE_DIR = "$WORKSPACE/packaging/licenses"
  env.ARTIFACT_ROOT = "$WORKSPACE/archive/zip"
  env.WARS_DIR = "$WORKSPACE/archive/war"
  env.DATE_TIME_STAMP = sh (script: "date +%Y%m%d%H%M", returnStdout:true).trim()
  env.VER = sh (script: "grep server.version= $WORKSPACE/build/build.properties | sed 's:server.version=::'", returnStdout:true).trim()
  env.ARTIFACT_DIR = "/var/www/server/core/${BUILD_TYPE}/HEAD"
  env.SUITE_BUILD_CAT = 'HEAD'
  env.SERVER_BUILD_CAT = 'HEAD'
  env.SUITE_VERSION = "$VER"
  env.SERVER_VERSION = "$VER"
  env.SONAR_HOST_URL = "http://sonar.boundlessgeo.com:9000"
  if ( BUILD_TYPE.equals('master') ) {
    env.SERVER_BRANCH = "master"
    env.MINOR_VERSION = "SNAPSHOT${DATE_TIME_STAMP}-${BUILD_NUMBER}"
    env.PACKAGE_VERSION = "$DATE_TIME_STAMP"
    env.NEXT_VER = sh ( script: "echo \$((PACKAGE_VERSION+1))", returnStdout:true).trim()
    env.DOCKER_VER = "nightly"
  } else {
    env.SERVER_BRANCH = "$VER"
    env.MINOR_VERSION = "server-${VER}"
    env.PACKAGE_VERSION = "$VER"
    env.NEXT_VER = "${PACKAGE_VERSION}.1"
    env.DOCKER_VER = "$BRANCH_NAME"
  }
  echo "DEBUG: PACKAGE_VERSION is ${PACKAGE_VERSION}"
  echo "DEBUG: NEXT_VER is ${NEXT_VER}"
  sh "env"
}

def setSpecVersions() {
  sh """
    sed -i "s/REPLACE_VERSION/${PACKAGE_VERSION}/" $WORKSPACE/suite/packaging/el/SPECS/*.spec
    sed -i "s/REPLACE_RELEASE/${BUILD_NUMBER}/" $WORKSPACE/suite/packaging/el/SPECS/*.spec
    sed -i "s/CURRENT_VER/${PACKAGE_VERSION}/g" $WORKSPACE/suite/packaging/el/SPECS/*.spec
  """
}

def captureHead() {
  env.SERVER_HEAD = sh (script: "cd $WORKSPACE/suite && git rev-parse --short HEAD", returnStdout:true).trim()
  env.WAR_ARCHIVE = "${ARCHIVE_BASENAME}-${SERVER_HEAD}-war"
  env.EXT_ARCHIVE = "${ARCHIVE_BASENAME}-${SERVER_HEAD}-ext"
}

def antBuild(def buildFile, def antTargets) {
  withCredentials([string(credentialsId: 'sonarQubeToken', variable: 'SONAR_QUBE_TOKEN')]) {
    workingDir = sh (script: "echo ${buildFile} | rev | cut -d / -f2- | rev", returnStdout:true).trim()
    antBuildFile = sh (script: "echo ${buildFile} | rev | cut -d / -f 1 | rev", returnStdout:true).trim()
    echo "workingDir = ${workingDir}"
    echo "antBuildFile = ${antBuildFile}"
    sh """
      cd $WORKSPACE/${workingDir}
      ant -file ${antBuildFile} \
      -Dserver.minor_version=-$MINOR_VERSION \
      -Dsuite.build_cat=$SERVER_BUILD_CAT \
      -Dserver.build_cat=$SERVER_BUILD_CAT \
      -Dsuite.version=$SUITE_VERSION \
      -Dserver.version=$SERVER_VERSION \
      -Dserver.build_branch=$BUILD_TYPE \
      $antTargets
    """
  }
}

def sonarScan(def targetDir, def projectName, def binary=false, def exclusions="") {
  withCredentials([string(credentialsId: 'sonarQubeToken', variable: 'SONAR_QUBE_TOKEN')]) {
    if ( binary ) {
      sh """
        sonar-scanner \
          -Dsonar.sources=${targetDir} \
          -Dsonar.projectName="[Server] ${projectName}" \
          -Dsonar.projectKey=org.boundlessgeo:${projectName.toLowerCase()} \
          -Dsonar.host.url=${SONAR_HOST_URL} \
          -Dsonar.login=${SONAR_QUBE_TOKEN} \
          -Dsonar.java.binaries=${targetDir} \
          -Dsonar.exclusions=${exclusions} \
      """
    } else {
      sh """
        sonar-scanner \
          -Dsonar.sources=${targetDir} \
          -Dsonar.projectName="[Server] ${projectName}" \
          -Dsonar.projectKey=org.boundlessgeo:${projectName.toLowerCase()} \
          -Dsonar.host.url=${SONAR_HOST_URL} \
          -Dsonar.login=${SONAR_QUBE_TOKEN} \
          -Dsonar.exclusions=${exclusions} \
      """
    }
  }
}

def archiveBuildZip(def component) {
  archiveFileSource = sh (script: "find ${ARTIFACT_DIR} -name *-${component}-${SERVER_HEAD}.zip", returnStdout:true).trim()
  archiveFileName = sh (script: "echo $archiveFileSource | rev | cut -d / -f 1 | rev", returnStdout:true).trim()
  sh "cp -p ${archiveFileSource} $WORKSPACE/archive/zip/"
  archiveArtifacts artifacts: "archive/zip/${archiveFileName}", fingerprint: true
}

def explodeSources() {
  sh """
    unzip archive/war/${ARCHIVE_BASENAME}-*-ext.zip -d $WORKSPACE/SRC
    mv $WORKSPACE/SRC/${ARCHIVE_BASENAME}-*-ext $WORKSPACE/SRC/${ARCHIVE_BASENAME}-ext
    unzip archive/war/${ARCHIVE_BASENAME}-*-war.zip -d $WORKSPACE/SRC
    mv $WORKSPACE/SRC/${ARCHIVE_BASENAME}-*-war $WORKSPACE/SRC/${ARCHIVE_BASENAME}-war
  """
  sourceWar = ['boundless-server-docs.war', 'composer.war', 'dashboard.war', 'geoserver.war', 'geowebcache.war', 'wpsbuilder.war']
  for (int i = 0; i < sourceWar.size(); i++) {
    echo "DEBUG: Exploding ${sourceWar[i]}"
    sourceWarName = sh (script: "echo ${sourceWar[i]} | rev | cut -d / -f 1 | rev", returnStdout:true).trim()
    sh "unzip $WORKSPACE/SRC/${ARCHIVE_BASENAME}-war/${sourceWar[i]} -d $WORKSPACE/SRC/${sourceWarName}"
  }
}

def packageWars() {
  makeDir("$EXT_ARCHIVE")
  makeDir("$WAR_ARCHIVE")
  sh "ln -s $ARTIFACT_ROOT/${BRANDING}-geomesa-accumulo-distributed-runtime-${SERVER_HEAD}.zip $ARTIFACT_ROOT/${BRANDING}-geoserver-geomesa-accumulo-${SERVER_HEAD}.zip"

  zip = ['app-schema', 'arcsde', 'csw', 'db2', 'gdal', 'geomesa-accumulo', 'grib', 'gsr', 'inspire', 'jdbcconfig', 'jdbcstore', 'jp2k', 'marlin', 'mongodb', 'netcdf', 'netcdf-out', 'oracle', 'sqlserver', 'vectortiles']
  for (int i = 0; i < zip.size(); i++) {
    echo "DEBUG: Processing ${zip[i]}"
    makeDir("${zip[i]}")
    sh """
      unzip -o $ARTIFACT_ROOT/${BRANDING}-geoserver-${zip[i]}-${SERVER_HEAD}.zip -d ${zip[i]}
    """
  }

  EE_EXTS = ['app-schema', 'arcsde', 'csw', 'db2', 'gdal', 'geomesa-accumulo', 'grib', 'gsr', 'inspire', 'jdbcconfig', 'jdbcstore', 'jp2k', 'marlin', 'mongodb', 'netcdf', 'netcdf-out', 'oracle', 'sqlserver', 'vectortiles']
  for (int i = 0; i < EE_EXTS.size(); i++) {
    sh """
      cd $EXT_ARCHIVE
      mv ../${EE_EXTS[i]}/ ${EE_EXTS[i]}
    """
  }

  sh """
    cd $EXT_ARCHIVE

    # renaming
    #mv hz-cluster cluster

    # Rename app schema to make consistent with agreed upon naming conventions
    mv app-schema appschema

    #mv python/ script/

    mkdir windows/
    cp "${WORKSPACE}/suite/packaging/windows/"* windows/

    cp $LICENSE_DIR/EULA ./
  """

  if ( BUILD_TYPE.equals('stable') ) {
    sh "mv $EXT_ARCHIVE ${ARCHIVE_BASENAME}-${PACKAGE_VERSION}-ext"
    env.EXT_ARCHIVE = "${ARCHIVE_BASENAME}-${PACKAGE_VERSION}-ext"
  }

  sh """
    zip -r ${EXT_ARCHIVE}.zip $EXT_ARCHIVE
    rm -rf ${EXT_ARCHIVE}/
    mv -f ${EXT_ARCHIVE}.zip $WARS_DIR/
  """

  WAR_ZIPS = ['dashboard', 'geoserver', 'composer', 'geowebcache', 'docs-war', 'wpsbuilder']
  for (int i = 0; i < WAR_ZIPS.size(); i++) {
    sh """
      unzip -jC $ARTIFACT_ROOT/${BRANDING}-${WAR_ZIPS[i]}-${SERVER_HEAD}.zip "*.war" -d ./$WAR_ARCHIVE
    """
  }

  sh "cp $WORKSPACE/archive/war/quickview.war $WORKSPACE/$WAR_ARCHIVE"

  makeDir("$WORKSPACE/repack")
  makeDir("$WORKSPACE/repack/doc")
  sh """
    cp $ARTIFACT_ROOT/boundless-server-data-dir-${SERVER_HEAD}.zip $WAR_ARCHIVE/boundless-server-data-dir.zip
    wget https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt -O $LICENSE_DIR/LGPLv2.1
    wget https://www.gnu.org/licenses/old-licenses/lgpl-2.0.txt -O $LICENSE_DIR/LGPLv2
    wget https://www.gnu.org/licenses/lgpl.txt -O $LICENSE_DIR/LGPLv3
    wget http://www.apache.org/licenses/LICENSE-2.0.txt -O $LICENSE_DIR/APACHEv2.0
  """

  war = ['composer', 'dashboard', 'geoserver', 'geowebcache', 'boundless-server-docs', 'wpsbuilder', 'quickview']
  for (int i = 0; i < war.size(); i++) {
    makeDir("$WORKSPACE/repack")
    makeDir("$WORKSPACE/repack/doc")
    env.THIS_WAR = "${war[i]}"
    sh """
      unzip $WAR_ARCHIVE/${THIS_WAR}.war -d $WORKSPACE/repack/
      rm -f $WAR_ARCHIVE/${THIS_WAR}.war
    """
    if ( THIS_WAR.equals('composer') ) {
      sh "cp -f $LICENSE_DIR/BSD $WORKSPACE/repack/doc/LICENSE"
    } else if ( THIS_WAR.equals('dashboard') ) {
      sh "cp -f $LICENSE_DIR/BSD $WORKSPACE/repack/doc/LICENSE"
    } else if ( THIS_WAR.equals('geoserver') ) {
      sh "cp -f $LICENSE_DIR/GPL $WORKSPACE/repack/doc/LICENSE"
    } else if ( THIS_WAR.equals('geowebcache') ) {
      sh "cp -f $LICENSE_DIR/LGPLv3 $WORKSPACE/repack/doc/LICENSE"
    } else if ( THIS_WAR.equals('boundless-server-docs') ) {
      echo "No additional license for docs needed."
    } else if ( THIS_WAR.equals('wpsbuilder') ) {
      sh "cp -f $LICENSE_DIR/APACHEv2.0 $WORKSPACE/repack/doc/LICENSE"
    } else if ( THIS_WAR.equals('quickview') ) {
      sh "cp -f $LICENSE_DIR/APACHEv2.0 $WORKSPACE/repack/doc/LICENSE"
    }
    sh """
      cp -f $LICENSE_DIR/EULA $WORKSPACE/repack/doc/
      cd $WORKSPACE/repack/
      jar -cvf $WORKSPACE/$WAR_ARCHIVE/${THIS_WAR}.war .
    """
  }

  if ( BUILD_TYPE.equals('stable') ) {
    sh "mv $WORKSPACE/$WAR_ARCHIVE $WORKSPACE/${ARCHIVE_BASENAME}-${PACKAGE_VERSION}-war"
    env.WAR_ARCHIVE = "${ARCHIVE_BASENAME}-${PACKAGE_VERSION}-war"
  }

  sh """
    zip -r ${WAR_ARCHIVE}.zip $WAR_ARCHIVE
    mv -f ${WAR_ARCHIVE}.zip $WARS_DIR/
    mv -f "$WAR_ARCHIVE/"*.war $WARS_DIR/
    rm -rf $WAR_ARCHIVE/
    rm -rf repack
  """
}

def packageRPMs(def component) {
  // Create RPM build tree
  dir = ['BUILD', 'BUILDROOT', 'RPMS', 'SOURCE', 'SPECS', 'SRPMS', 'SRC']
  for (int i = 0; i < dir.size(); i++) {
    makeDir("$WORKSPACE/${BRANDING}-${component}/${dir[i]}")
  }

  // Add RPM Spec file, set versions
  sh """
    cp $WORKSPACE/suite/packaging/el/SPECS/${BRANDING}-${component}.spec $WORKSPACE/${BRANDING}-${component}/SPECS
    sed -i "s/REPLACE_VERSION/${PACKAGE_VERSION}/" $WORKSPACE/${BRANDING}-${component}/SPECS/${BRANDING}-${component}.spec
    sed -i "s/REPLACE_RELEASE/$BUILD_NUMBER/" $WORKSPACE/${BRANDING}-${component}/SPECS/${BRANDING}-${component}.spec
    sed -i "s/CURRENT_VER/${PACKAGE_VERSION}/g" $WORKSPACE/${BRANDING}-${component}/SPECS/${BRANDING}-${component}.spec
    sed -i "s/NEXT_VER/${NEXT_VER}/g" $WORKSPACE/${BRANDING}-${component}/SPECS/${BRANDING}-${component}.spec
  """

  // Build RPM
  sh """
    rpmbuild -vv -ba --define "_source_filedigest_algorithm 8" --define "_binary_filedigest_algorithm 8" --define "_topdir $WORKSPACE/${BRANDING}-${component}" --define "_WORKSPACE $WORKSPACE" $WORKSPACE/${BRANDING}-${component}/SPECS/${BRANDING}-${component}.spec
  """
}

def debConvert(def component, def ubuntuVer) {
  env.DEST_DIR = "/tmp/convert-${BUILD_TYPE}/debs-out/ubuntu/${ubuntuVer}"
  env.DEPS = sh (script: "grep ${component}= ${WORKSPACE}/suite/packaging/ubuntu/${ubuntuVer}/depends.esv | cut -d '=' -f2-", returnStdout:true).trim()
  env.CONFLICTS = sh (script: "grep ${component}= ${WORKSPACE}/suite/packaging/ubuntu/${ubuntuVer}/conflicts.esv | cut -d '=' -f2-", returnStdout:true).trim()
  env.REPLACES = sh (script: "grep ${component}= ${WORKSPACE}/suite/packaging/ubuntu/${ubuntuVer}/replaces.esv | cut -d '=' -f2-", returnStdout:true).trim()

  sh """
    ssh root@priv-repo.boundlessgeo.com '
      mkdir -p $DEST_DIR
      if [ -d /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component ]; then
        rm -rf /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component
      fi
      mkdir -p /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component
      cd /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component

      cp /tmp/convert-${BUILD_TYPE}/rpms-in/${BRANDING}-${component}-${PACKAGE_VERSION}-*.rpm /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component
    '
  """

  sh """
    ssh root@priv-repo.boundlessgeo.com '
      cd /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/$component
      alien -cdgk *.rpm
      rm -rf *.orig *.rpm
      cd ${BRANDING}-${component}-${PACKAGE_VERSION}
      sed -i "s/^Depends: /Depends: ${DEPS}, /" debian/control

      if [ -n "$REPLACES" ]; then
        sed -i "/^Depends/aReplaces: $REPLACES" debian/control
      fi

      if [ -n "$CONFLICTS" ]; then
        sed -i "/^Depends/aConflicts: $CONFLICTS" debian/control
      fi

      debian/rules binary

      mv /tmp/convert-${BUILD_TYPE}/${ubuntuVer}/${component}/*.deb $DEST_DIR
    '
  """
}

def trimRepo() {
  productPackages = ['composer', 'dashboard', 'docs', 'geoserver', 'geowebcache', 'gs-appschema', 'gs-arcsde', 'gs-csw', 'gs-db2', 'gs-gdal', 'gs-geomesa-accumulo', 'gs-grib', 'gs-gsr', 'gs-inspire', 'gs-jdbcconfig', 'gs-jdbcstore', 'gs-jp2k', 'gs-mongodb', 'gs-netcdf', 'gs-netcdf-out', 'gs-oracle', 'gs-sqlserver', 'gs-vectortiles', 'wpsbuilder', 'quickview']
  for (int i = 0; i < productPackages.size(); i++) {
    sh """
      ssh root@priv-repo.boundlessgeo.com 'ls -t /var/www/repo/suite/${BRANCH_NAME}/el/6/${BRANDING}-${productPackages[i]}-*.rpm | tail -n +3 | xargs rm -- || true'
      ssh root@priv-repo.boundlessgeo.com 'ls -t /var/www/repo/suite/${BRANCH_NAME}/el/7/${BRANDING}-${productPackages[i]}-*.rpm | tail -n +3 | xargs rm -- || true'
      ssh root@priv-repo.boundlessgeo.com 'ls -t /var/www/repo/suite/${BRANCH_NAME}/ubuntu/14/${BRANDING}-${productPackages[i]}_*.deb | tail -n +3 | xargs rm -- || true'
      ssh root@priv-repo.boundlessgeo.com 'ls -t /var/www/repo/suite/${BRANCH_NAME}/ubuntu/16/${BRANDING}-${productPackages[i]}_*.deb | tail -n +3 | xargs rm -- || true'
    """
  }
}
