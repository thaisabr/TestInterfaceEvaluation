package testCodeAnalyser.ruby

import groovy.util.logging.Slf4j
import org.jrubyparser.ast.FCallNode
import org.jrubyparser.ast.StrNode
import org.jrubyparser.ast.SymbolNode
import org.jrubyparser.util.NoopVisitor
import util.ruby.RubyUtil

@Slf4j
class ConfigRoutesVisitor extends NoopVisitor {

    def file
    def routingMethods = [] //name, file, value

    ConfigRoutesVisitor(def file){
        this.file = file
    }

    private createArgVisitor(){
        def argsVisitor = new NoopVisitor(){
            def values = [] as Set

            @Override
            Object visitStrNode(StrNode strNode) {
                super.visitStrNode(strNode)
                values += strNode.value
                strNode
            }
        }
        argsVisitor
    }

    def registryRoutes(FCallNode iVisited){
        def argsVisitor = createArgVisitor()
        def args = []
        iVisited.childNodes().each{
            it.accept(argsVisitor)
            if(argsVisitor.values && !argsVisitor.values.empty) args += argsVisitor.values
        }
        args = args.unique()
        if(args.size()==1) routingMethods += [name:iVisited.name, file:RubyUtil.ROUTES_ID, value:iVisited.name, arg:args.first()]
        else if(args.size()==2) routingMethods += [name:args.get(0), file:RubyUtil.ROUTES_ID, value:args.get(0), arg:args.get(1)]
    }

    @Override
    Object visitFCallNode(FCallNode iVisited) {
        super.visitFCallNode(iVisited)

        switch (iVisited.name){
            case "resources": //partial solution
                log.info "RESOURCES: $iVisited"
                List<SymbolNode> entities = iVisited.args.childNodes().findAll{ it instanceof SymbolNode }
                entities?.each{ entity ->
                    def name = entity.name.substring(0,entity.name.length()-1)
                    log.info "resource value: ${entity.name}; line:${iVisited.position.startLine+1}"
                    routingMethods += [name:entity.name, file:RubyUtil.ROUTES_ID, value:"/${entity.name}", arg:"${entity.name}#index"]
                    routingMethods += [name:"new_$name", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/new", arg:"${entity.name}#new"]

                    //check if it works. If an id is passed as argument, the string will be '//edit'
                    routingMethods += [name:"edit_$name", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/edit", arg:"${entity.name}#edit"]

                    routingMethods += [name:name, file:RubyUtil.ROUTES_ID, value:"/${entity.name}/", arg:"${entity.name}#show"]
                }

                //dealing with 'only' restriction (partial solution). Example: resources :comments, only: [:index, :new, :create]
                /*def constraints = (iVisited.args.childNodes() - entities)?.first()
                def args = constraints?.childNodes()?.first()?.childNodes()?.find{ it instanceof ArrayNode }
                args = args?.childNodes()?.findAll{ it instanceof SymbolNode }*.name
                log.info "constrain args: ${args}"*/
                break
            case "get":
                log.info "GET: $iVisited"
                registryRoutes(iVisited)
                break
            case "root":
                log.info "ROOT: $iVisited"
                registryRoutes(iVisited)
                break
            case "match":
                log.info "MATCH: $iVisited"
                registryRoutes(iVisited)
                break
            case "devise_for": //devise is a gem for authentication
                log.info "DEVISE_FOR: $iVisited"
                List<SymbolNode> entities = iVisited.args.childNodes().findAll{ it instanceof SymbolNode }
                entities?.each{ entity ->
                    def name = entity.name.substring(0,entity.name.length()-1)
                    log.info "resource value: $name; line:${iVisited.position.startLine+1}"
                    //Authenticatable (default)
                    routingMethods += [name:"new_${name}_session", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/sign_in", arg:"devise/sessions#new"]
                    routingMethods += [name:"${name}_session", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/sign_in", arg:"devise/sessions#create"]
                    routingMethods += [name:"destroy_${name}_session", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/sign_out", arg:"devise/sessions#destroy"]
                    //Recoverable
                    routingMethods += [name:"new_${name}_password", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/password/new", arg:"devise/passwords#new"]
                    routingMethods += [name:"edit_${name}_password", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/password/edit", arg:"devise/passwords#edit"]
                    routingMethods += [name:"${name}_password", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/password", arg:"devise/passwords#update"]
                    //Confirmable
                    routingMethods += [name:"new_${name}_confirmation", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/confirmation/new", arg:"devise/confirmations#new"]
                    routingMethods += [name:"${name}_confirmation", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/confirmation", arg:"devise/confirmations#show"]
                    //Registerable
                    routingMethods += [name:"new_${name}_registration", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/registration/new", arg:"devise/registrations#new"]
                    routingMethods += [name:"edit_${name}_registration", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/registration/edit", arg:"devise/registrations#edit"]
                    routingMethods += [name:"${name}_registration", file:RubyUtil.ROUTES_ID, value:"/${entity.name}/registration", arg:"devise/registrations#update"]

                    //To conclude:
                    //Rememberable
                    //Trackable
                    //Validatable
                    //Lockable
                    //routingMethods += [name:"new_${name}_registration", file:Util.ROUTES_ID, value:?, arg:"devise/unlocks#new"]
                    //routingMethods += [name:"edit_{$name}_registration", file:Util.ROUTES_ID, value:?, arg:"devise/unlocks#edit"]
                    //routingMethods += [name:"${name}_registration", file:Util.ROUTES_ID, value:?, arg:"devise/unlocks#update"]
                    //Timeoutable
                    //Omniauthable
                }
                break
            case "mount": //calls rake application
            case "redirect": //it is used with "get" and others; it does not require treatment
            case "devise_scope": //it is not important for the study purpose
            case "namespace": //it a grouping mechanism. until the moment, it is ignored.
            case "post": //visil call is a get request; it is not necessary to extract routes from post, put, patch or delete
            case "put":
            case "patch":
            case "delete":
                break
            default: log.info "UNKNOWN ROUTE OPTION: ${iVisited.name} (line ${iVisited.position.startLine+1})"
        }
        iVisited
    }

}
