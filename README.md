# remote-test-refresh

[![Build Status](https://travis-ci.org/minhtuannguyen/remote-test-refresh.svg?branch=master)](https://travis-ci.org/minhtuannguyen/remote-test-refresh)
[![Coverage Status](https://coveralls.io/repos/github/minhtuannguyen/remote-test-refresh/badge.svg?branch=master)](https://coveralls.io/github/minhtuannguyen/remote-test-refresh?branch=master)
[![Dependencies Status](http://jarkeeper.com/minhtuannguyen/remote-test-refresh/status.svg)](http://jarkeeper.com/minhtuannguyen/remote-test-refresh)


[![Clojars Project](http://clojars.org/minhtuannguyen/remote-test-refresh/latest-version.svg)](https://clojars.org/minhtuannguyen/remote-test-refresh)

`remote-test-refresh` is a leiningen plugin which uses SSH to synchronizes code changes between local and remote machine automatically. When running, `remote-test-refresh` will scan for all source and test resources defined in the project.clj. When detecting change, `remote-test-refresh` will transfer the diff per ssh and apply it to the remote repository.

In summary, `remote-test-refresh` offer possibilities:
   +  to synchronize code change between local and remote repository over ssh.
   +  to run command (.i.e start application, run tests) on the remote repository and stream its output to local machine 
   +  to forward port between remote and local machine. It's useful to test application interactively.
   
This picture explains how `remote-test-refresh` works:   
   
```ruby

     |----Local Machine----|        remote-test-refresh        |----Remote Machine---|  
     |                     |     <----------SSH---------->     |                     | 
     | +--parent-folder    |      + transfer code change       | +--parent-folder    | 
     |    +-- project-1    |      + port forwarding            |   +-- project-1     | 
     |    +-- project-2    |      + run command                |   +-- project-2     | 
     |---------------------|                                   |---------------------|  
	                                                       
```
   
To configure `remote-test-refresh`,  you can define `:remote-test` in your .lein/profiles.clj.

```clojure
:remote-test {:user              "your-ssh-user"                ;required for ssh connection
		      :host              "host-name-or-ip"              ;required for ssh connection
		      :with-system-agent true                           ;required for ssh connection 
	          :remote-path       "repo/parent/folder/on/remote" ;required for sync code change
	          :forwarding-port   9001                           ;optional for port forwarding
	          :command           "lein run"                     ;optional for running cmd
	         }
```


`remote-path` specifies the  absolute path of the parent folder on remote machine. `remote-test-refresh` must know it in order to apply code change to project repository correctly.

if `:remote-test` can not be found in the project.clj/profiles, `remote-test-refresh` will ask you all those parameters at the runtime. 

If `:with-system-agent` is set to false, `remote-test-refresh` you will use a separated ssh-agent to connect to remote machine. In this case, `remote-test-refresh` will ask you for ssh authentication at the runtime.

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

# Issues
If you have this problem under ubuntu when using system agent: java.lang.UnsatisfiedLinkError: Unable to load library 'c': /usr/lib/x86_64-linux-gnu/libc.so: invalid ELF header

    $ cd /lib/x86_64-linux-gnu
    $ sudo ln -s libc.so.6 libc.so
    ...

## License

Copyright © 2016 Distributed under the Eclipse Public License version 1.0.