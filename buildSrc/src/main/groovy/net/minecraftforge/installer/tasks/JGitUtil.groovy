package net.minecraftforge.installer.tasks

import org.eclipse.jgit.errors.RepositoryNotFoundException

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId

public class Util {

    public static def gitInfo(dir) {
        def git = null
        try {
            git = Git.open(dir)
        } catch (final RepositoryNotFoundException ignored) {
            return [
                tag: '0.0',
                offset: '0',
                hash: '00000000',
                branch: 'master',
                commit: '0000000000000000000000',
                abbreviatedId: '00000000'
            ]
        }
        def desc = git.describe().setLong(true).setTags(true).call().rsplit('-', 2)
        def head = git.repository.resolve('HEAD')
        
        def ret = [:]
        ret.tag = desc[0]
        ret.offset = desc[1]
        ret.hash = desc[2]
        ret.branch = git.repository.branch
        ret.commit = ObjectId.toString(head)
        ret.abbreviatedId = head.abbreviate(8).name()
        
        return ret
    }
    
    public static String getVersion(info) {
		return "${info.tag}.${info.offset}".toString()
    }
}