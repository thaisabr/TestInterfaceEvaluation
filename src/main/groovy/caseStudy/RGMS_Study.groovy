package caseStudy

import au.com.bytecode.opencsv.CSVWriter
import br.ufpe.cin.tan.evaluation.TaskInterfaceEvaluator
import groovy.util.logging.Slf4j
import br.ufpe.cin.tan.analysis.task.DoneTask
import br.ufpe.cin.tan.analysis.task.TodoTask
import br.ufpe.cin.tan.util.ConstantData

@Slf4j
class RGMS_Study {

    static CSVWriter writer = new CSVWriter(new FileWriter(ConstantData.DEFAULT_EVALUATION_FILE))

    static analyseTask(DoneTask done, TodoTask todo) {
        def ireal = done?.computeRealInterface()
        def itest = todo?.computeTestBasedInterface()

        if (!itest?.toString()?.isEmpty()) {
            def precision = TaskInterfaceEvaluator.calculateFilesPrecision(itest, ireal)
            def recall = TaskInterfaceEvaluator.calculateFilesRecall(itest, ireal)
            String[] line = [done.id, itest, ireal, precision, recall]
            writer.writeNext(line)

            log.info "Task id: ${done.id}"
            log.info "ITEST:"
            log.info "${itest}"
            log.info "IREAL:"
            log.info "${ireal}"
            log.info "Files precision: $precision"
            log.info "Files recall: $recall"
        } else {
            log.warn "ITEST is empty!"
        }

    }

    public static void main(String[] args) {
        String[] header = ["Task", "ITest", "IReal", "Precision", "Recall"]
        writer.writeNext(header)

        def repositoryUrl = "https://github.com/spgroup/rgms"

        /* TASK 1: Dissertation autofill. */
        def featurePath = "Dissertacao.feature"
        def doneT1 = new DoneTask(repositoryUrl, "1", ["bd99265b39ba8bc233a368994157bd6960dcfdbe"])
        def todoT1 = new TodoTask(repositoryUrl, true, "1", [[path: featurePath, lines: [87]]])
        analyseTask(doneT1, todoT1)

        /* TASK 2: Tool autofill. */
        featurePath = "Ferramenta.feature"
        def doneT2 = new DoneTask(repositoryUrl, "2", ["0d675ff970175bd7b96bb46abc45fddae08c2662"])
        def todoT2 = new TodoTask(repositoryUrl, true, "2", [[path: featurePath, lines: [61]]])
        analyseTask(doneT2, todoT2)

        /* TASK 3: technical report autofill. */
        featurePath = "TechnicalReport.feature"
        def doneT3 = new DoneTask(repositoryUrl, "3", ["8bc4d3fe9b637aedb7c2a08a45107d498ff35d68"])
        def todoT3 = new TodoTask(repositoryUrl, true, "3", [[path: featurePath, lines: [60]]])
        analyseTask(doneT3, todoT3)

        /* TASK 4: Create and update of research group news from twitter. */
        featurePath = "News.feature"
        def doneT4 = new DoneTask(repositoryUrl, "4", ["7e0c8915620cc9cdf0e21171703c06866834115e"])
        def todoT4 = new TodoTask(repositoryUrl, true, "4", [[path: featurePath, lines: [25, 31]]])
        analyseTask(doneT4, todoT4) //7,13,19,25,31

        /* TASK 5: Articles CRUD. */
        featurePath = "Article.feature"
        def doneT5 = new DoneTask(repositoryUrl, "5", ["ad05d1fce8f646a8d1dea4871a6c0807faed10a1",
                                                       "e9080a8b39212226bef5bb171e03c92ce0c5402e",
                                                       "131046e51f3cbe1f852dc3f2965cf181cdaeabbc"])
        def todoT5 = new TodoTask(repositoryUrl, true, "5", [[path: featurePath, lines: [129, 135, 141, 147, 152, 157, 164, 171,
                                                                                         178, 185, 191, 198]]])
        analyseTask(doneT5, todoT5)

        /* TASK 6: Book CRUD, including XML import. */
        featurePath = "Book.feature"
        def doneT6 = new DoneTask(repositoryUrl, "6", ["80ba5995f4bfb5962b93a0d17a76b4d7cbd4b18e"])
        def todoT6 = new TodoTask(repositoryUrl, true, "6", [[path: featurePath, lines: [7, 12, 17, 22, 27, 32]]])
        analyseTask(doneT6, todoT6)

        /* TASK 7: Autentication and creation of new user.
        Problem with moved files (AuthController.groovy and Member.groovy). */
        featurePath = "Authentication.feature"
        def doneT7 = new DoneTask(repositoryUrl, "7", ["eaad515896ddbdf2d33e361fe14be25a1b93e30a"])
        def todoT7 = new TodoTask(repositoryUrl, true, "7", [[path: featurePath, lines: [5, 11, 17, 23, 28, 33, 39, 46, 51, 59]]])
        analyseTask(doneT7, todoT7)

        /* TASK 8: User autofill. */
        featurePath = "Member.feature"
        def doneT8 = new DoneTask(repositoryUrl, "8", ["06c151b9492c1a8870552254e15378c33727e7cd"])
        def todoT8 = new TodoTask(repositoryUrl, true, "8", [[path: featurePath, lines: [71, 75]]])
        analyseTask(doneT8, todoT8)

        /* TASK 9: Export data about researcher and research group using XML and HTML formats. */
        featurePath = "Reports.feature"
        def doneT9 = new DoneTask(repositoryUrl, "9", ["31623a936df4530d1928cb6581ab949d8b74fff9"])
        def todoT9 = new TodoTask(repositoryUrl, true, "9", [[path: featurePath, lines: [5, 11, 25, 35, 44, 76]]])
        analyseTask(doneT9, todoT9)

        /* TASK 10: Research lines in research groups.
        Problem with moved files (ResearchLineController.groovy and ResearchLine.groovy). */
        featurePath = "ResearchLine.feature"
        def doneT10 = new DoneTask(repositoryUrl, "10", ["34f0a83124c79136dbfe34258637309f0aa9d34a"])
        def todoT10 = new TodoTask(repositoryUrl, true, "10", [[path: featurePath, lines: [6, 11, 16, 26, 32, 40]]])
        analyseTask(doneT10, todoT10)

        /* TASK 11: Research groups CRUD.
        Problem with moved files (ResearchGroupController.groovy and ResearchGroup.groovy). */
        featurePath = "ResearchGroup.feature"
        def doneT11 = new DoneTask(repositoryUrl, "11", ["2eae58bf838b97101cfd71a070341e58ecb3bc88"])
        def todoT11 = new TodoTask(repositoryUrl, true, "11", [[path: featurePath, lines: [4, 9, 14, 19, 24, 30, 35, 41, 46, 51, 57, 64]]])
        analyseTask(doneT11, todoT11)

        /* TASK 12: BookChapter autofill. */
        featurePath = "BookChapter.feature"
        def doneT12 = new DoneTask(repositoryUrl, "12", ["222992bf214e8faf7a780a2d79159a5c986e56ff"])
        def todoT12 = new TodoTask(repositoryUrl, true, "12", [[path: featurePath, lines: [48]]])
        analyseTask(doneT12, todoT12)

        /* TASK 13: Import research line by XML.
        Problemas: tangled commit; de 6 arquivos alterados, apenas 4 dizem respeito
        a importação de researchline de fato); 2 cenários de teste e 1 deles não tem 1 step implementado, daí que antes
        não se calculava interface para cenário com implementação parcial, agora sim. */
        featurePath = "ResearchLine.feature"
        def doneT13 = new DoneTask(repositoryUrl, "13", ["fc2fb42cd838f9879f1d3a01400880ccbea33f0e"])
        def todoT13 = new TodoTask(repositoryUrl, true, "13", [[path: featurePath, lines: [51, 56]]])
        analyseTask(doneT13, todoT13)

        /* TASK 14: Import orientation (production and test tasks).
        Problema: após a realização dessa tarefa o código foi refatorado e certas classes de IReal não existem mais (coisa de ProjectMember)
        e então jamais o teste poderia referenciá-las, já que ele é analisado em sua versão mais atual. Sim, o acesso a XMLService via teste não
        acontece porque o cálculo da interface com base no teste não analisa o código de produção. Caso sim, saberia que essa classe é usada indiretamente. */
        featurePath = "Orientation.feature"
        def doneT14 = new DoneTask(repositoryUrl, "14", ["9a4b207c9ee86bc7ba57524546247f967f001a74"])
        def todoT14 = new TodoTask(repositoryUrl, true, "14", [[path: featurePath, lines: [69, 74]]])
        analyseTask(doneT14, todoT14)

        /* TASK 15: Publication bibtex.
        Tem step que está com o corpo vazio. */
        def path1 = "BibtexGenerateFile.feature"
        def path2 = "Reports.feature"
        def doneT15 = new DoneTask(repositoryUrl, "15", ["7efa9eb9afe541b48a68a9b8dfbcde50170b82b0"])
        def todoT15 = new TodoTask(repositoryUrl, true, "15", [[path: path1, lines: [6]], [path: path2, lines: [53, 61]]])
        analyseTask(doneT15, todoT15)

        /* TASK 16: Import bibtex. */
        featurePath = "BibtexImport.feature"
        def doneT16 = new DoneTask(repositoryUrl, "16", ["95bbfa305f699246e1eadbf9ae476e7a6508142d"])
        def todoT16 = new TodoTask(repositoryUrl, true, "16", [[path: featurePath, lines: [7, 14, 21]]])
        analyseTask(doneT16, todoT16)

        /* TASK 17: Create and remove news about research group.
        Engloba TASK 4. Tarefa composta definida por 2 commits. A tarefa é produção + teste. Os testes foram identificados no próprio commit. */
        featurePath = "News.feature"
        def doneT17 = new DoneTask(repositoryUrl, "17", ["3c4ba349749950b49a73177e20f741cc5ae74f78",
                                                         "1b9a4c4c993f9a6ce73c8e9b06c69146d624b672"])
        def todoT17 = new TodoTask(repositoryUrl, true, "17", [[path: featurePath, lines: [7, 13, 19, 25, 31]]])
        analyseTask(doneT17, todoT17)

        /* CENÁRIO PROBLEMÁTICO PORQUE É TANGLED
        featurePath = "ResearchProject.feature"
        def doneT18 = new DoneTask(repositoryUrl, "18", ["ec4e96487c60555f3bfd35f072adccc80ccd46db"])
        def todoT18 = new TodoTask(repositoryUrl, true, "18", [ [path:featurePath, lines:[33,39]] ])
        analyseTask(doneT18, todoT18) */

        writer.close()
    }

}
