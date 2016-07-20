# remote-test-refresh

`remote-test-refresh` synchronizes automatically with remote project over ssh when files change. When running, `remote-test-refresh` will scan for all source and test resources defined in the project.cls. When detecting change, `remote-test-refresh` will transfer the diff per ssh and apply it to the to the remote repository.

To define the remote repository, you can define `:remote-test` in the source project.cls or in your .lein/profiles.clj.

```clojure
:remote-test {:user  "your-username-on-remote-machine"
		      :host  "your.host.name-or-ip"
	          :remote-path "/path/to/your/repo/on/remote/machine"}
```

To start `remote-test-refresh` :

    $ lein remote-test-refresh
    * Change has been transfered successfully
    * Change has been transfered successfully
    ...
    

## License

Copyright © 2016 

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.