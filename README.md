# remote-test-refresh

[![Build Status](https://travis-ci.org/minhtuannguyen/remote-test-refresh.svg?branch=master)](https://travis-ci.org/minhtuannguyen/remote-test-refresh)
[![Coverage Status](https://coveralls.io/repos/github/minhtuannguyen/remote-test-refresh/badge.svg?branch=master)](https://coveralls.io/github/minhtuannguyen/remote-test-refresh?branch=master)
[![Dependencies Status](http://jarkeeper.com/minhtuannguyen/remote-test-refresh/status.svg)](http://jarkeeper.com/minhtuannguyen/remote-test-refresh)


[![Clojars Project](http://clojars.org/minhtuannguyen/remote-test-refresh/latest-version.svg)](https://clojars.org/minhtuannguyen/remote-test-refresh)

`remote-test-refresh` is a leiningen plugin which synchronizes automatically changes of local repository with other repository on remote machine over ssh. When running, `remote-test-refresh` will scan for all source and test resources defined in the project.clj. When detecting change, `remote-test-refresh` will transfer the diff per ssh and apply it to the to the remote repository.

In summary, `remote-test-refresh` offer possibilities:
   +  to synchronize code change between local and remote repository over ssh.
   +  to run command (.i.e start application) on the remote repository and stream its output. 
   +  to forward port from the remote host to your local maschine. It's useful to test interactively.  
   
This picture explains how `remote-test-refresh` works:   
   
```ruby

     |----Local Machine----|        remote-test-refresh        |----Remote Machine---|  
     |                     |     <----------SSH---------->     |                     | 
     | +--parent-folder    |      + transfer code change       | +--parent-folder    | 
     |    +-- project-1    |      + port forwaring             |   +-- project-1     | 
     |    +-- project-2    |      + run command                |   +-- project-2     | 
     |---------------------|                                   |---------------------|  
	                                                       
```
   
To configure `remote-test-refresh`,  you can define `:remote-test` in your .lein/profiles.clj.

```clojure
:remote-test {:user              "your-username-on-remote-machine" ;required for ssh connection
		      :host              "your.host.name-or-ip"            ;required for ssh connection
		      :with-system-agent true                              ;required for ssh connection 
	          :remote-path       "repo/parent/folder/on/remote"    ;required for sync code change
	          :forwarding-port   9001                              ;optional for port forwarding
	          :command           "lein run"                        ;optional for running cmd
	         }
```


if `:remote-test` can not be found in the project, `remote-test-refresh` will ask you all those parameters at the runtime. If `:with-system-agent` is set to false, `remote-test-refresh` you will ue a separated ssh-agent to connect to remote host. `remote-test-refresh` will ask you for ssh auth at runtime.

To start `remote-test-refresh` :

    $ lein remote-test-refresh
    * Remote-Test-Refresh version: 0.1.6
    * ==> Which command do you want to run on the repository of remote machine (optional): lein run  
    * ==> Enter port if you need a port to be forwarded (optional): 8080
    
    * Starting with the parameters: {:repo "repo", :user "user", :auth {:with-system-agent false, :password ***}, :host 1.2.3.4, :remote-path /folder/path/}
    * Starting session the parameters: {:username "user", :strict-host-key-checking :no, :password ***, :use-system-ssh-agent false}
    
    * Change has been transfered successfully to your remote repository
      Application starting ...
    ...
    

## License

Copyright © 2016 
Distributed under the Eclipse Public License version 1.0.