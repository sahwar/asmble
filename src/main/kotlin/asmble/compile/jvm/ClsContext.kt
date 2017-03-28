package asmble.compile.jvm

import asmble.ast.Node
import asmble.util.Either
import asmble.util.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

data class ClsContext(
    val packageName: String,
    val className: String,
    val mod: Node.Module,
    val cls: ClassNode = ClassNode().also { it.name = (packageName.replace('.', '/') + "/$className").trimStart('/') },
    val mem: Mem = ByteBufferMem,
    val reworker: InsnReworker = InsnReworker,
    val logger: Logger = Logger.Print(Logger.Level.OFF),
    val funcBuilder: FuncBuilder = FuncBuilder,
    val nonAdjacentMemAccessesRequiringLocalVar: Int = 3,
    val eagerFailLargeMemOffset: Boolean = true,
    val preventMemIndexOverflow: Boolean = false,
    val checkConversionOverflow: Boolean = true
) : Logger by logger {
    val importFuncs: List<Node.Import> by lazy { mod.imports.filter { it.kind is Node.Import.Kind.Func } }
    val importGlobals: List<Node.Import> by lazy { mod.imports.filter { it.kind is Node.Import.Kind.Global } }
    val thisRef = TypeRef(Type.getObjectType((packageName.replace('.', '/') + "/$className").trimStart('/')))

    fun funcAtIndex(index: Int) = importFuncs.getOrNull(index).let {
        when (it) {
            null -> Either.Right(mod.funcs.getOrNull(index - importFuncs.size) ?: throw CompileErr.UnknownFunc(index))
            else -> Either.Left(it)
        }
    }

    fun funcTypeAtIndex(index: Int) = funcAtIndex(index).let {
        when (it) {
            is Either.Left -> mod.types[(it.v.kind as Node.Import.Kind.Func).typeIndex]
            is Either.Right -> it.v.type
        }
    }

    fun globalAtIndex(index: Int) = importGlobals.getOrNull(index).let {
        when (it) {
            null -> Either.Right(mod.globals.getOrNull(importGlobals.size - index) ?: error("No global at $index"))
            else -> Either.Left(it)
        }
    }

    fun importGlobalGetterFieldName(index: Int) = "import\$get" + globalName(index)
    fun importGlobalSetterFieldName(index: Int) = "import\$set" + globalName(index)
    fun globalName(index: Int) = "\$global$index"
    fun funcName(index: Int) = "\$func$index"

    val checkedF2SIConv: MethodInsnNode by lazy {
        val method = funcBuilder.buildF2SICheckedConv(this)
        cls.methods.add(method)
        MethodInsnNode(Opcodes.INVOKESTATIC, thisRef.asmName, method.name, method.desc, false)
    }
}