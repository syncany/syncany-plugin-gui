Syncany GUI Readme
==================

Syncany GUI is based on the following principles

- **SWT Library** graphical library used is SWT

Launch Syncany GUI
------------------

1. Checkout Syncany on the command line 

        cd /home/user/workplace
        git clone http://github.com/syncany/syncany-plugin-gui
        cd syncany-plugin-gui
	./gradlew clean pluginjar -Pos=linux -P=arch=x86_64
	sy plugin install build/libs/*.jar
		
2. Start Syncany GUI client

        sy gui
		
For Ubuntu Unity users (from Ubuntu 13) you need to first install the following dependencies to get tray icon properly working
	
	sudo apt-get install python-appindicator
	sudo apt-get install python-pip
	sudo pip install websocket-client
      
