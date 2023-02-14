package dev.fastmc.loader;

import cpw.mods.modlauncher.api.*;
import dev.fastmc.loader.core.Constants;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuppressWarnings("NullableProblems")
public class ForgeTransformingLoader implements ITransformationService {
    @Override
    public String name() {
        return Constants.MOD_NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {

    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<ITransformer> transformers() {
        return Collections.singletonList(new Transformer());
    }

    private static class Transformer implements ITransformer<ClassNode> {
        @Override
        public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
            try {
                InjectClassLoader.hackInModuleClassLoader(
                    Thread.currentThread().getContextClassLoader(),
                    ForgeLoader.UNPACKED_LIB
                );
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            return input;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public Set<Target> targets() {
            return Collections.singleton(Target.targetPreClass("net.minecraft.client.main.Main"));
        }
    }
}