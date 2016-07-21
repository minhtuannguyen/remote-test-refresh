# remote-test-refresh

[![Build Status](https://travis-ci.org/minhtuannguyen/remote-test-refresh.svg?branch=master)](https://travis-ci.org/minhtuannguyen/remote-test-refresh)
[![Coverage Status](https://coveralls.io/repos/github/minhtuannguyen/remote-test-refresh/badge.svg?branch=master)](https://coveralls.io/github/minhtuannguyen/minhtuannguyen?branch=master)
[![Dependencies Status](http://jarkeeper.com/minhtuannguyen/remote-test-refresh/status.svg)](http://jarkeeper.com/minhtuannguyen/remote-test-refresh)


[![Clojars Project](http://clojars.org/minhtuannguyen/remote-test-refresh/latest-version.svg)](https://clojars.org/minhtuannguyen/remote-test-refresh)

`remote-test-refresh` synchronizes automatically with remote project over ssh when files change. When running, `remote-test-refresh` will scan for all source and test resources defined in the project.clj. When detecting change, `remote-test-refresh` will transfer the diff per ssh and apply it to the to the remote repository.

To define the remote repository, you can define `:remote-test` in the source project.cls or in your .lein/profiles.clj.

```clojure
:remote-test {:user         "your-username-on-remote-machine"
		      :host         "your.host.name-or-ip"
	          :remote-path  "/path/to/parent/folder/of/repo/on/remote/machine"}
```

To start `remote-test-refresh` :

    $ lein remote-test-refresh
    * Change has been transfered successfully to your remote repo
    * Change has been transfered successfully to your remote repo
    ...
    

## License

Copyright © 2016 

Distributed under the Eclipse Public License either version 1.0.