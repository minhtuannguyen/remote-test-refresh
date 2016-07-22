# remote-test-refresh

[![Build Status](https://travis-ci.org/minhtuannguyen/remote-test-refresh.svg?branch=master)](https://travis-ci.org/minhtuannguyen/remote-test-refresh)
[![Coverage Status](https://coveralls.io/repos/github/minhtuannguyen/remote-test-refresh/badge.svg?branch=master)](https://coveralls.io/github/minhtuannguyen/remote-test-refresh?branch=master)
[![Dependencies Status](http://jarkeeper.com/minhtuannguyen/remote-test-refresh/status.svg)](http://jarkeeper.com/minhtuannguyen/remote-test-refresh)


[![Clojars Project](http://clojars.org/minhtuannguyen/remote-test-refresh/latest-version.svg)](https://clojars.org/minhtuannguyen/remote-test-refresh)

`remote-test-refresh` is a leiningen plugin which synchronizes automatically changes of local repository with other repository on remote machine over ssh. When running, `remote-test-refresh` will scan for all source and test resources defined in the project.clj. When detecting change, `remote-test-refresh` will transfer the diff per ssh and apply it to the to the remote repository.

Moreover, `remote-test-refresh` offer possibilities:
   +  to run command (.i.e start application) on the remote repository and stream its output. 
   +  to forward port from the remote host to your local maschine. It's useful to test interactively.  
   
To define the remote repository, you can define `:remote-test` in your .lein/profiles.clj.

```clojure
:remote-test {:user             "your-username-on-remote-machine" ;required
              :password         "secret" ;required
		      :host             "your.host.name-or-ip" ;required
	          :remote-path      "/path/to/parent/folder/of/repo/on/remote/machine" ;required
	          :forwarding-port  9001 ;optional
	          :command          "lein run" ;optional 
	         }
```

if `:remote-test` can not be found in the project, `remote-test-refresh` will ask you all those parameters at the runtime.

To start `remote-test-refresh` :

    $ lein remote-test-refresh
    * Remote-Test-Refresh version: 0.1.3
    * ==> Please enter your ssh password:
    * Starting with the parameters: {:repo "repo", :user "user", :password ***, :host 1.2.3.4, :remote-path /folder/path/ }
    
    * Change has been transfered successfully to your remote repo
    * Change has been transfered successfully to your remote repo
    ...
    

## License

Copyright © 2016 

Distributed under the Eclipse Public License either version 1.0.