import com.fasterxml.jackson.databind.ObjectMapper
import com.iqmsoft.ratpack.groovy.api.command.LoginCommand;
import com.iqmsoft.ratpack.groovy.datastore.MongoConfig
import com.iqmsoft.ratpack.groovy.datastore.MongoConnection
import com.iqmsoft.ratpack.groovy.handlers.ErrorHandler
import com.iqmsoft.ratpack.groovy.jackson.ObjectIdObjectMapper
import com.iqmsoft.ratpack.groovy.user.User
import com.iqmsoft.ratpack.groovy.user.UserModule
import com.iqmsoft.ratpack.groovy.user.UserService
import ratpack.config.ConfigData
import ratpack.error.ServerErrorHandler
import ratpack.exec.Blocking


import static ratpack.groovy.Groovy.ratpack
import static ratpack.jackson.Jackson.json

ratpack {

    bindings {
        ConfigData configData = ConfigData.of { c ->
            c.props("$serverConfig.baseDir.file/application.properties")
            c.env()
            c.sysProps()
        }

        bindInstance(ServerErrorHandler, new ErrorHandler())

        bindInstance(MongoConfig, configData.get("/mongo", MongoConfig))
        bind(MongoConnection)

        module UserModule
        add(ObjectMapper.class, new ObjectIdObjectMapper())
    }

    handlers {
        all {
            response.headers.add 'Access-Control-Allow-Origin', '*'
            response.headers.add 'Access-Control-Allow-Methods', 'GET,PUT,POST,DELETE'
            response.headers.add 'Access-Control-Allow-Headers', 'X-Auth-Token, Content-Type,X-Requested-With'
            next()
        }

        post('create') { UserService userService ->
            parse(User).then { user ->
                Blocking.get {
                    userService.createNewUser(user)
                }.then {
                    render json(message: 'user created')
                }
            }
        }

        post('login') { UserService userService ->
            parse(LoginCommand).then { command ->
                User user
                def key
                Blocking.get {
                    user = userService.getUserByEmail(command.username)

                    if(!user) {
                        throw new IllegalAccessException()
                    }

                    if (user?.password == userService.generatePassword(user, command.password)) {
                        key = userService.createToken(user)
                    } else {
                        throw new IllegalAccessException()
                    }
                }.then {
                    render json([auth: key])
                }
            }

        }

        prefix('api') {
            def user
            all {UserService userService ->
                def tokenString = request.headers.get('X-Auth-Token')
                Blocking.get {
                    user = userService.getUserByToken(tokenString)
                }.then {
                    next()
                }
            }
            get('users') {UserService userService ->
                def users = userService.list()
                render json(users)
            }
        }

    }
}
